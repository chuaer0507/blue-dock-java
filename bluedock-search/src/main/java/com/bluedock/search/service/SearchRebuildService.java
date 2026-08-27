package com.bluedock.search.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 管理员触发全量重建：发 Kafka rebuild 事件，由 worker 扫表回填。 */
@Service
public class SearchRebuildService {
  private static final Set<String> ALLOWED =
      Set.of(
          SearchIndexEvent.TYPE_CONTACT,
          SearchIndexEvent.TYPE_PROJECT,
          SearchIndexEvent.TYPE_TASK,
          SearchIndexEvent.TYPE_FILE,
          SearchIndexEvent.TYPE_MESSAGE);

  private final SearchIndexPublisher publisher;
  private final UserAccountRepository users;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public SearchRebuildService(
      SearchIndexPublisher publisher,
      UserAccountRepository users,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.publisher = publisher;
    this.users = users;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> start(String typesParam) {
    requireAdmin();
    List<String> types = parseTypes(typesParam);
    Boolean locked =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.searchRebuildLock(), "1", Duration.ofHours(2));
    if (Boolean.FALSE.equals(locked)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SEARCH_REBUILD_BUSY);
    }
    String eventId = UUID.randomUUID().toString().replace("-", "");
    String typesCsv = String.join(",", types);
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("state", "queued");
    status.put("eventId", eventId);
    status.put("types", types);
    status.put("startedAt", System.currentTimeMillis());
    writeStatus(status);

    publisher.publish(
        new SearchIndexEvent(
            eventId,
            SearchIndexEvent.ACTION_REBUILD,
            "all",
            0L,
            AuthContext.requireUserId(),
            0L,
            typesCsv,
            typesCsv));
    return status;
  }

  public Map<String, Object> status() {
    requireAdmin();
    String raw = redis.opsForValue().get(RedisKeys.searchRebuildStatus());
    if (raw == null || raw.isBlank()) {
      return Map.of("state", "idle");
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<>() {});
    } catch (Exception e) {
      return Map.of("state", "unknown");
    }
  }

  private void writeStatus(Map<String, Object> status) {
    try {
      redis
          .opsForValue()
          .set(
              RedisKeys.searchRebuildStatus(),
              objectMapper.writeValueAsString(status),
              Duration.ofHours(24));
    } catch (Exception ignored) {
      // best-effort
    }
  }

  private List<String> parseTypes(String typesParam) {
    if (typesParam == null || typesParam.isBlank() || "all".equalsIgnoreCase(typesParam.trim())) {
      return List.copyOf(ALLOWED);
    }
    Set<String> out = new LinkedHashSet<>();
    for (String part : typesParam.split("[,\\s]+")) {
      String t = part.trim().toLowerCase(Locale.ROOT);
      if (t.isEmpty()) {
        continue;
      }
      if (!ALLOWED.contains(t)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SEARCH_REBUILD_TYPES);
      }
      out.add(t);
    }
    if (out.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SEARCH_REBUILD_TYPES);
    }
    return new ArrayList<>(out);
  }

  private void requireAdmin() {
    long userId = AuthContext.requireUserId();
    String identity =
        users
            .findByUserId(userId)
            .map(u -> u.getIdentity() == null ? "" : u.getIdentity())
            .orElse("");
    if (!identity.contains("admin")) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.ADMIN_REQUIRED);
    }
  }
}
