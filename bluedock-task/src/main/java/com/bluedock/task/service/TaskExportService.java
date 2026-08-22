package com.bluedock.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.export.ExportRunPublisher;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.service.AdminGuard;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class TaskExportService {
  private static final int MAX_USERS = 100;
  private static final int MAX_DAYS = 90;

  private final AdminGuard adminGuard;
  private final ExportRunPublisher exportPublisher;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public TaskExportService(
      AdminGuard adminGuard,
      ExportRunPublisher exportPublisher,
      ObjectProvider<NotifySendPublisher> notifyPublisher,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.adminGuard = adminGuard;
    this.exportPublisher = exportPublisher;
    this.notifyPublisher = notifyPublisher;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> exportStats(String userIdRaw, String timeRaw, String type) {
    adminGuard.requireAdmin();
    long me = AuthContext.requireUserId();
    List<Long> userIds = parseUserIds(userIdRaw);
    if (userIds.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_PARAM_REQUIRED);
    }
    if (userIds.size() > MAX_USERS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_USER_LIMIT, MAX_USERS);
    }
    String[] range = parseTimeRange(timeRaw);
    String timeType = normalizeType(type);
    String eventId = UUID.randomUUID().toString().replace("-", "");
    exportPublisher.publish(
        new ExportRunEvent(
            eventId,
            ExportRunEvent.KIND_TASK_STATS,
            me,
            userIds,
            range[0],
            range[1],
            timeType));
    notifyProgress(me, "正在导出任务统计，请稍等...");
    return Map.of("accepted", true, "eventId", eventId);
  }

  public Map<String, Object> exportOverdue() {
    adminGuard.requireAdmin();
    long me = AuthContext.requireUserId();
    String eventId = UUID.randomUUID().toString().replace("-", "");
    exportPublisher.publish(
        new ExportRunEvent(
            eventId,
            ExportRunEvent.KIND_TASK_OVERDUE,
            me,
            List.of(),
            null,
            null,
            ExportRunEvent.TIME_TASK));
    notifyProgress(me, "正在导出超期任务，请稍等...");
    return Map.of("accepted", true, "eventId", eventId);
  }

  public ResponseEntity<InputStreamResource> download(String key) throws IOException {
    long me = AuthContext.requireUserId();
    if (key == null || key.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_KEY_INVALID);
    }
    String raw = redis.opsForValue().get(RedisKeys.exportDown(key.trim()));
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.EXPORT_KEY_EXPIRED);
    }
    Map<String, Object> meta = objectMapper.readValue(raw, new TypeReference<>() {});
    long owner = ((Number) meta.getOrDefault("userId", 0L)).longValue();
    if (owner != me) {
      throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.EXPORT_DENIED);
    }
    String path = String.valueOf(meta.getOrDefault("path", ""));
    String name = String.valueOf(meta.getOrDefault("name", "export.csv"));
    Path file = Path.of(path);
    if (!Files.isRegularFile(file)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.EXPORT_KEY_EXPIRED);
    }
    InputStream in = Files.newInputStream(file);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .contentLength(Files.size(file))
        .body(new InputStreamResource(in));
  }

  private void notifyProgress(long userId, String body) {
    NotifySendPublisher pub = notifyPublisher.getIfAvailable();
    if (pub == null) {
      return;
    }
    pub.publish(
        new NotifySendEvent(
            IdGenerator.nextId() + "",
            NotifySendEvent.CHANNEL_DESKTOP,
            List.of(userId),
            "任务导出",
            body,
            Map.of()));
  }

  private static String normalizeType(String type) {
    if (type == null || type.isBlank() || ExportRunEvent.TIME_TASK.equals(type)) {
      return ExportRunEvent.TIME_TASK;
    }
    if (ExportRunEvent.TIME_CREATED.equals(type)) {
      return ExportRunEvent.TIME_CREATED;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TYPE_INVALID);
  }

  private String[] parseTimeRange(String timeRaw) {
    if (timeRaw == null || timeRaw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_PARAM_REQUIRED);
    }
    List<String> parts = new ArrayList<>();
    String t = timeRaw.trim();
    if (t.startsWith("[")) {
      try {
        parts.addAll(objectMapper.readValue(t, new TypeReference<List<String>>() {}));
      } catch (Exception e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
      }
    } else {
      for (String p : t.split(",")) {
        if (!p.isBlank()) {
          parts.add(p.trim());
        }
      }
    }
    if (parts.size() < 2) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    LocalDate start;
    LocalDate end;
    try {
      start = LocalDate.parse(parts.get(0).substring(0, Math.min(10, parts.get(0).length())));
      end = LocalDate.parse(parts.get(1).substring(0, Math.min(10, parts.get(1).length())));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_INVALID);
    }
    if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_TIME_RANGE, MAX_DAYS);
    }
    return new String[] {start.toString(), end.toString()};
  }

  private List<Long> parseUserIds(String raw) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String t = raw.trim();
    if (t.startsWith("[")) {
      try {
        List<?> list = objectMapper.readValue(t, List.class);
        for (Object o : list) {
          long id = asLong(o);
          if (id > 0) {
            ids.add(id);
          }
        }
      } catch (Exception e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EXPORT_PARAM_REQUIRED);
      }
    } else {
      for (String p : t.split(",")) {
        long id = asLong(p.trim());
        if (id > 0) {
          ids.add(id);
        }
      }
    }
    return List.copyOf(ids);
  }

  private static long asLong(Object o) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o != null) {
      try {
        return Long.parseLong(String.valueOf(o).trim());
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }
    return 0L;
  }
}
