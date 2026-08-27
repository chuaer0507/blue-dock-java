package com.bluedock.assistant.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.assistant.domain.AssistantSession;
import com.bluedock.assistant.repo.AssistantFeedbackRepository;
import com.bluedock.assistant.repo.AssistantSearchLogRepository;
import com.bluedock.assistant.repo.AssistantSessionRepository;
import com.bluedock.assistant.web.dto.AssistantSessionView;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.ai.OpenAiCompatibleChatClient;
import com.bluedock.system.ai.AiBotChatService;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.AiBotSettingService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantService {
  private static final Duration STREAM_TTL = Duration.ofMinutes(10);
  private static final int MAX_NEW_IMAGES = 20;
  private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

  private final AssistantSessionRepository sessions;
  private final AssistantFeedbackRepository feedbacks;
  private final AssistantSearchLogRepository searchLogs;
  private final SettingRepository settings;
  private final StringRedisTemplate redis;
  private final RealtimeFanoutPublisher fanout;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<ObjectStorage> objectStorage;
  private final ObjectProvider<AiBotChatService> aiChat;

  public AssistantService(
      AssistantSessionRepository sessions,
      AssistantFeedbackRepository feedbacks,
      AssistantSearchLogRepository searchLogs,
      SettingRepository settings,
      StringRedisTemplate redis,
      RealtimeFanoutPublisher fanout,
      ObjectMapper objectMapper,
      ObjectProvider<ObjectStorage> objectStorage,
      ObjectProvider<AiBotChatService> aiChat) {
    this.sessions = sessions;
    this.feedbacks = feedbacks;
    this.searchLogs = searchLogs;
    this.settings = settings;
    this.redis = redis;
    this.fanout = fanout;
    this.objectMapper = objectMapper;
    this.objectStorage = objectStorage;
    this.aiChat = aiChat;
  }

  public Map<String, Object> auth(
      String modelType, String modelName, Object context, String locale, String sessionId, String fd) {
    long userId = AuthContext.requireUserId();
    String loc = normalizeLocale(locale);
    String streamKey = UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("userId", userId);
    payload.put("modelType", nullToEmpty(modelType));
    payload.put("modelName", nullToEmpty(modelName));
    payload.put("locale", loc);
    payload.put("sessionId", truncate(nullToEmpty(sessionId), 100));
    payload.put("fd", nullToEmpty(fd));
    payload.put("context", context == null ? List.of() : context);
    try {
      redis
          .opsForValue()
          .set(RedisKeys.assistantStream(streamKey), objectMapper.writeValueAsString(payload), STREAM_TTL);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_OP_INVALID);
    }
    return Map.of("streamKey", streamKey);
  }

  public Map<String, Object> models() {
    AuthContext.requireUserId();
    String json = settings.findSettingJson("aiBotSetting").orElse("{}");
    try {
      Map<String, Object> root =
          objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
      if (root == null) {
        return Map.of();
      }
      AiBotSettingService.normalizeLegacyKeys(root);
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : root.entrySet()) {
        String key = e.getKey();
        if (key.endsWith("Models") || (key.endsWith("Model") && !"model".equals(key))) {
          out.put(key, e.getValue());
        }
      }
      return out;
    } catch (Exception e) {
      return Map.of();
    }
  }

  /**
   * 元素匹配：优先 OpenAI 兼容 embedding 余弦相似度；不可用或失败回退词法打分。
   *
   * @return {@code matches} + {@code strategy}=embedding|lexical
   */
  public Map<String, Object> matchElements(String query, List<Map<String, Object>> elements, Integer topK) {
    AuthContext.requireUserId();
    String qRaw = nullToEmpty(query).trim();
    String q = qRaw.toLowerCase(Locale.ROOT);
    if (q.isEmpty() || elements == null || elements.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_OP_INVALID);
    }
    int k = topK == null ? 10 : Math.min(Math.max(topK, 1), 50);

    List<Map<String, Object>> candidates = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (Map<String, Object> el : elements) {
      if (el == null) {
        continue;
      }
      Object nameObj = el.get("name");
      if (nameObj == null) {
        continue;
      }
      String name = String.valueOf(nameObj);
      if (name.isBlank()) {
        continue;
      }
      candidates.add(el);
      names.add(name);
      if (candidates.size() >= 100) {
        break;
      }
    }
    if (candidates.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_OP_INVALID);
    }

    List<Map<String, Object>> scored = matchByEmbedding(qRaw, candidates, names);
    String strategy = "embedding";
    if (scored == null) {
      strategy = "lexical";
      scored = matchByLexical(q, candidates, names);
    }
    scored.sort(Comparator.comparingDouble(m -> -((Double) m.get("similarity"))));
    if (scored.size() > k) {
      scored = scored.subList(0, k);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("matches", scored);
    out.put("strategy", strategy);
    return out;
  }

  private List<Map<String, Object>> matchByEmbedding(
      String query, List<Map<String, Object>> candidates, List<String> names) {
    AiBotChatService chat = aiChat.getIfAvailable();
    if (chat == null || !chat.available()) {
      return null;
    }
    List<String> inputs = new ArrayList<>(names.size() + 1);
    inputs.add(query);
    inputs.addAll(names);
    List<float[]> vectors = chat.embed(inputs);
    if (vectors == null || vectors.size() != inputs.size()) {
      return null;
    }
    float[] qVec = vectors.get(0);
    List<Map<String, Object>> scored = new ArrayList<>(candidates.size());
    for (int i = 0; i < candidates.size(); i++) {
      double sim = OpenAiCompatibleChatClient.cosineSimilarity(qVec, vectors.get(i + 1));
      // 归一到 [0,1] 展示（余弦约 [-1,1]）
      double normalized = Math.max(0, Math.min(1, (sim + 1) / 2));
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("element", candidates.get(i));
      row.put("similarity", normalized);
      scored.add(row);
    }
    return scored;
  }

  private static List<Map<String, Object>> matchByLexical(
      String qLower, List<Map<String, Object>> candidates, List<String> names) {
    List<Map<String, Object>> scored = new ArrayList<>(candidates.size());
    for (int i = 0; i < candidates.size(); i++) {
      String name = names.get(i).toLowerCase(Locale.ROOT);
      double sim = lexicalSimilarity(qLower, name);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("element", candidates.get(i));
      row.put("similarity", sim);
      scored.add(row);
    }
    return scored;
  }

  @Transactional
  public void logSearch(
      String query,
      String locale,
      String source,
      String contextKey,
      Long dialogId,
      List<Object> sourceIds,
      Double topScore,
      Integer resultCount,
      Integer durationMs) {
    long userId = AuthContext.requireUserId();
    String q = truncate(nullToEmpty(query).trim(), 500);
    if (q.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_OP_INVALID);
    }
    String src = nullToEmpty(source);
    if (!"chat".equals(src) && !"invoke".equals(src)) {
      src = "";
    }
    String loc = nullToEmpty(locale);
    if (!"zh-CN".equals(loc) && !"en-US".equals(loc)) {
      loc = "";
    }
    List<Object> ids = sourceIds == null ? List.of() : sourceIds;
    if (ids.size() > 10) {
      ids = ids.subList(0, 10);
    }
    String idsJson;
    try {
      idsJson = objectMapper.writeValueAsString(ids);
    } catch (Exception e) {
      idsJson = "[]";
    }
    double score = topScore == null ? 0 : Math.max(0, Math.min(1, topScore));
    int count = resultCount == null ? 0 : Math.max(0, resultCount);
    int dur = durationMs == null ? 0 : Math.max(0, durationMs);
    searchLogs.insert(
        userId,
        dialogId == null ? 0 : Math.max(0, dialogId),
        truncate(nullToEmpty(contextKey), 191),
        src,
        q,
        loc,
        idsJson,
        score,
        count,
        dur);
  }

  @Transactional
  public Map<String, Object> feedbackSave(
      String sessionKey,
      String sessionId,
      Long localId,
      String feedback,
      String prompt,
      String answer,
      List<Object> sourceIds,
      String model) {
    long userId = AuthContext.requireUserId();
    String key = truncate(nullToEmpty(sessionKey).isEmpty() ? "default" : sessionKey.trim(), 100);
    String sid = truncate(nullToEmpty(sessionId).trim(), 100);
    long lid = localId == null ? 0 : localId;
    if (sid.isEmpty() || lid <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_FEEDBACK_INVALID);
    }
    String fb = nullToEmpty(feedback).trim();
    if (!fb.isEmpty() && !"like".equals(fb) && !"dislike".equals(fb)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_FEEDBACK_INVALID);
    }
    if (fb.isEmpty()) {
      feedbacks.delete(userId, key, sid, lid);
      return Map.of("feedback", "");
    }
    String ans = truncate(nullToEmpty(answer), 2000);
    List<Object> ids = sourceIds == null ? List.of() : sourceIds;
    if (ids.size() > 10) {
      ids = ids.subList(0, 10);
    }
    String idsJson;
    try {
      idsJson = objectMapper.writeValueAsString(ids);
    } catch (Exception e) {
      idsJson = "[]";
    }
    feedbacks.upsert(
        userId,
        key,
        sid,
        lid,
        fb,
        truncate(nullToEmpty(prompt), 1000),
        ans,
        md5(ans),
        idsJson,
        truncate(nullToEmpty(model), 100));
    return Map.of("feedback", fb);
  }

  public Map<String, Object> operationDispatch(String fd, String action, Object payload) {
    long userId = AuthContext.requireUserId();
    String act = nullToEmpty(action).trim();
    if (act.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_OP_INVALID);
    }
    String sessionFd = nullToEmpty(fd).trim();
    if (!sessionFd.isEmpty()) {
      String owner = redis.opsForValue().get(RedisKeys.wsSession(sessionFd));
      if (owner == null || !Long.toString(userId).equals(owner)) {
        throw new BusinessException(ErrorCodes.ASSISTANT_DENIED, I18nKeys.ASSISTANT_OP_INVALID);
      }
    } else {
      var online = redis.opsForSet().members(RedisKeys.wsUser(userId));
      if (online == null || online.isEmpty()) {
        throw new BusinessException(ErrorCodes.ASSISTANT_DENIED, I18nKeys.ASSISTANT_OP_INVALID);
      }
    }
    String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("requestId", requestId);
    data.put("action", act);
    data.put("payload", payload == null ? Map.of() : payload);
    if (!sessionFd.isEmpty()) {
      data.put("fd", sessionFd);
    }
    fanout.publish(
        new RealtimeFanoutEvent(
            UUID.randomUUID().toString().replace("-", ""),
            RealtimeEventTypes.OPERATION,
            List.of(userId),
            data));
    return Map.of("requestId", requestId);
  }

  public Map<String, Object> operationResult(String requestId) {
    long userId = AuthContext.requireUserId();
    String reportId = nullToEmpty(requestId).trim();
    if (reportId.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_OP_INVALID);
    }
    String key = RedisKeys.assistantOp(reportId);
    String raw = redis.opsForValue().get(key);
    if (raw == null || raw.isBlank()) {
      return Map.of("status", "pending");
    }
    try {
      JsonNode row = objectMapper.readTree(raw);
      long owner = row.path("userId").asLong(0);
      if (owner != userId) {
        throw new BusinessException(ErrorCodes.FORBIDDEN, I18nKeys.ASSISTANT_OP_INVALID);
      }
      redis.delete(key);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "ready");
      out.put("success", row.path("success").asBoolean(false));
      if (row.has("result")) {
        out.put("result", objectMapper.convertValue(row.get("result"), Object.class));
      } else {
        out.put("result", null);
      }
      if (row.has("error") && !row.get("error").isNull()) {
        out.put("error", row.get("error").asString());
      } else {
        out.put("error", null);
      }
      return out;
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      redis.delete(key);
      return Map.of("status", "pending");
    }
  }

  public List<AssistantSessionView> sessionList(String sessionKey) {
    long userId = AuthContext.requireUserId();
    String key = nullToEmpty(sessionKey).isEmpty() ? "default" : sessionKey.trim();
    List<AssistantSessionView> list = new ArrayList<>();
    for (AssistantSession s : sessions.listByUserAndKey(userId, key)) {
      list.add(toView(s));
    }
    return list;
  }

  @Transactional
  public Map<String, Object> sessionSave(
      String sessionKey,
      String sessionId,
      String sceneKey,
      String title,
      Object data,
      Object newImages) {
    long userId = AuthContext.requireUserId();
    String key = nullToEmpty(sessionKey).isEmpty() ? "default" : sessionKey.trim();
    String sid = truncate(nullToEmpty(sessionId).trim(), 100);
    if (sid.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_SESSION_ID);
    }
    String dataJson;
    try {
      dataJson = objectMapper.writeValueAsString(data == null ? List.of() : data);
    } catch (Exception e) {
      dataJson = "[]";
    }

    Map<String, String> images = new LinkedHashMap<>();
    var exist = sessions.find(userId, key, sid);
    if (exist.isPresent() && exist.get().getImagesJson() != null) {
      try {
        Map<String, String> loaded =
            objectMapper.readValue(
                exist.get().getImagesJson(), new TypeReference<Map<String, String>>() {});
        if (loaded != null) {
          images.putAll(loaded);
        }
      } catch (Exception ignored) {
        // keep empty
      }
    }

    Map<String, String> imageUrls = mergeNewImages(userId, images, newImages);
    String imagesJson;
    try {
      imagesJson = objectMapper.writeValueAsString(images);
    } catch (Exception e) {
      imagesJson = "{}";
    }

    AssistantSession s = new AssistantSession();
    s.setUserId(userId);
    s.setSessionKey(truncate(key, 100));
    s.setSessionId(sid);
    s.setSceneKey(truncate(nullToEmpty(sceneKey), 100));
    s.setTitle(truncate(nullToEmpty(title), 255));
    s.setDataJson(dataJson);
    s.setImagesJson(imagesJson);
    sessions.upsert(s);
    return Map.of("imageUrls", imageUrls);
  }

  /**
   * 将 {@code newImages} 落盘并合并进会话 images 映射。
   *
   * <p>支持：Map&lt;id, base64|dataUrl|url&gt;；或 List[{@code id}/{@code key} + {@code
   * content}/{@code data}/{@code url}]。
   */
  Map<String, String> mergeNewImages(
      long userId, Map<String, String> images, Object newImages) {
    Map<String, String> incoming = normalizeNewImages(newImages);
    if (incoming.isEmpty()) {
      return Map.of();
    }
    if (incoming.size() > MAX_NEW_IMAGES) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_LIMIT, MAX_NEW_IMAGES);
    }
    Map<String, String> uploaded = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : incoming.entrySet()) {
      String id = truncate(e.getKey().trim(), 100);
      if (id.isEmpty()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
      }
      String url = resolveImageUrl(userId, id, e.getValue());
      images.put(id, url);
      uploaded.put(id, url);
    }
    return uploaded;
  }

  private Map<String, String> normalizeNewImages(Object raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (e.getKey() == null || e.getValue() == null) {
          continue;
        }
        out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
      }
      return out;
    }
    if (raw instanceof List<?> list) {
      int i = 0;
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          Object id = first(m, "id", "key");
          Object content = first(m, "content", "data", "url", "base64");
          if (id == null || content == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
          }
          out.put(String.valueOf(id), String.valueOf(content));
        } else if (item != null) {
          out.put("img" + (i++), String.valueOf(item));
        }
      }
    }
    return out;
  }

  private static Object first(Map<?, ?> m, String... keys) {
    for (String k : keys) {
      if (m.containsKey(k) && m.get(k) != null) {
        return m.get(k);
      }
    }
    return null;
  }

  private String resolveImageUrl(long userId, String imageId, String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
    }
    if (value.startsWith("http://")
        || value.startsWith("https://")
        || value.startsWith("media/")
        || value.startsWith("/")) {
      return truncate(value, 2000);
    }
    String contentType = "image/png";
    String ext = "png";
    String b64 = value;
    if (value.startsWith("data:")) {
      int comma = value.indexOf(',');
      if (comma < 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
      }
      String meta = value.substring(5, comma).toLowerCase(Locale.ROOT);
      b64 = value.substring(comma + 1);
      if (meta.contains("image/jpeg") || meta.contains("image/jpg")) {
        contentType = "image/jpeg";
        ext = "jpg";
      } else if (meta.contains("image/webp")) {
        contentType = "image/webp";
        ext = "webp";
      } else if (meta.contains("image/gif")) {
        contentType = "image/gif";
        ext = "gif";
      } else if (meta.contains("image/png")) {
        contentType = "image/png";
        ext = "png";
      } else if (!meta.startsWith("image/")) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
      }
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
    }
    if (bytes.length == 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_INVALID);
    }
    if (bytes.length > MAX_IMAGE_BYTES) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_TOO_LARGE, MAX_IMAGE_BYTES / (1024 * 1024));
    }
    ObjectStorage storage = objectStorage.getIfAvailable();
    if (storage == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_STORAGE);
    }
    String ym = LocalDate.now(ZoneOffset.UTC).format(YM);
    String key =
        "media/assistant/"
            + userId
            + "/"
            + ym
            + "/"
            + UUID.randomUUID().toString().replace("-", "")
            + "."
            + ext;
    try {
      return storage.put(key, new ByteArrayInputStream(bytes), bytes.length, contentType);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_IMAGE_STORAGE);
    }
  }

  @Transactional
  public void sessionDelete(String sessionKey, String sessionId, Boolean clearAll) {
    long userId = AuthContext.requireUserId();
    String key = nullToEmpty(sessionKey).isEmpty() ? "default" : sessionKey.trim();
    if (Boolean.TRUE.equals(clearAll)) {
      sessions.deleteAll(userId, key);
      return;
    }
    String sid = nullToEmpty(sessionId).trim();
    if (sid.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_SESSION_ID);
    }
    int n = sessions.deleteOne(userId, key, sid);
    if (n == 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.ASSISTANT_NOT_FOUND);
    }
  }

  private AssistantSessionView toView(AssistantSession s) {
    Object responses;
    try {
      responses = objectMapper.readValue(s.getDataJson() == null ? "[]" : s.getDataJson(), Object.class);
    } catch (Exception e) {
      responses = List.of();
    }
    Map<String, String> images;
    try {
      images =
          objectMapper.readValue(
              s.getImagesJson() == null ? "{}" : s.getImagesJson(),
              new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      images = Map.of();
    }
    long created =
        s.getCreatedAt() == null ? 0 : s.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    long updated =
        s.getUpdatedAt() == null ? 0 : s.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    return new AssistantSessionView(
        s.getSessionId(),
        s.getTitle(),
        responses,
        images == null ? Map.of() : images,
        s.getSceneKey(),
        created,
        updated);
  }

  private static String normalizeLocale(String locale) {
    String loc = nullToEmpty(locale);
    if ("en-US".equalsIgnoreCase(loc)) {
      return "en-US";
    }
    return "zh-CN";
  }

  private static double lexicalSimilarity(String query, String name) {
    if (name.contains(query) || query.contains(name)) {
      return 0.9;
    }
    String[] qParts = query.split("\\s+");
    int hit = 0;
    for (String p : qParts) {
      if (!p.isBlank() && name.contains(p)) {
        hit++;
      }
    }
    if (hit == 0) {
      return 0;
    }
    return Math.min(0.85, (double) hit / qParts.length);
  }

  private static String md5(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      return "";
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
