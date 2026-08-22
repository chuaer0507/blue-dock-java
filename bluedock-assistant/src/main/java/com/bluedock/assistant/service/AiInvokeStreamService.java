package com.bluedock.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.ai.OpenAiChatException;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.ai.AiBotChatService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 助手流式推理：消费 {@code assistant/auth} 签发的 streamKey，SSE 推送 append/done。 */
@Service
public class AiInvokeStreamService {
  private static final Logger log = LoggerFactory.getLogger(AiInvokeStreamService.class);
  private static final long SSE_TIMEOUT_MS = 180_000L;
  private static final ExecutorService EXEC =
      Executors.newVirtualThreadPerTaskExecutor();

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final AiBotChatService chat;

  public AiInvokeStreamService(
      StringRedisTemplate redis, ObjectMapper objectMapper, AiBotChatService chat) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.chat = chat;
  }

  public SseEmitter stream(String streamKey) {
    String key = streamKey == null ? "" : streamKey.trim();
    if (key.isEmpty() || key.length() > 64) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_STREAM_INVALID);
    }
    String redisKey = RedisKeys.assistantStream(key);
    String raw = redis.opsForValue().getAndDelete(redisKey);
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_STREAM_EXPIRED);
    }
    Map<String, Object> payload;
    try {
      payload = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_STREAM_INVALID);
    }
    if (!chat.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_AI_UNAVAILABLE);
    }

    List<Map<String, String>> messages = buildMessages(payload);
    if (messages.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.ASSISTANT_STREAM_INVALID);
    }
    String modelOverride = str(payload.get("modelName"));

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    EXEC.execute(
        () -> {
          try {
            chat.chatStream(
                modelOverride.isBlank() ? null : modelOverride,
                messages,
                delta -> sendAppend(emitter, delta));
            sendDone(emitter, Map.of());
            emitter.complete();
          } catch (OpenAiChatException e) {
            log.warn("ai invoke stream failed: {}", e.toString());
            safeDoneError(emitter, Messages.get(I18nKeys.ASSISTANT_AI_FAILED));
          } catch (Exception e) {
            log.warn("ai invoke stream error: {}", e.toString());
            safeDoneError(emitter, Messages.get(I18nKeys.ASSISTANT_AI_FAILED));
          }
        });
    return emitter;
  }

  private List<Map<String, String>> buildMessages(Map<String, Object> payload) {
    List<Map<String, String>> out = new ArrayList<>();
    String system = chat.systemPrompt();
    if (system != null && !system.isBlank()) {
      out.add(Map.of("role", "system", "content", system.trim()));
    }
    Object ctx = payload.get("context");
    if (ctx instanceof String s && !s.isBlank()) {
      try {
        ctx = objectMapper.readValue(s, Object.class);
      } catch (Exception ignored) {
        // keep string
      }
    }
    if (ctx instanceof List<?> list) {
      for (Object item : list) {
        Map<String, String> row = toMessage(item);
        if (row != null) {
          out.add(row);
        }
      }
    }
    return out;
  }

  private static Map<String, String> toMessage(Object item) {
    if (item instanceof List<?> pair && pair.size() >= 2) {
      String role = normalizeRole(String.valueOf(pair.get(0)));
      String content = contentOf(pair.get(1));
      if (content.isBlank()) {
        return null;
      }
      return Map.of("role", role, "content", content);
    }
    if (item instanceof Map<?, ?> map) {
      Object roleObj = map.containsKey("role") ? map.get("role") : map.get("0");
      Object contentObj = map.containsKey("content") ? map.get("content") : map.get("message");
      if (contentObj == null && map.containsKey("1")) {
        contentObj = map.get("1");
      }
      String role = normalizeRole(roleObj == null ? "user" : String.valueOf(roleObj));
      String content = contentOf(contentObj);
      if (content.isBlank()) {
        return null;
      }
      return Map.of("role", role, "content", content);
    }
    return null;
  }

  private static String contentOf(Object raw) {
    if (raw == null) {
      return "";
    }
    if (raw instanceof String s) {
      return s.trim();
    }
    if (raw instanceof List<?> || raw instanceof Map<?, ?>) {
      // 多模态块：退化为 JSON 字符串，避免丢上下文
      return String.valueOf(raw).trim();
    }
    return String.valueOf(raw).trim();
  }

  private static String normalizeRole(String role) {
    String r = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
    return switch (r) {
      case "system" -> "system";
      case "assistant" -> "assistant";
      default -> "user";
    };
  }

  private void sendAppend(SseEmitter emitter, String delta) {
    if (delta == null || delta.isEmpty()) {
      return;
    }
    try {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("content", delta);
      emitter.send(SseEmitter.event().name("append").data(data));
    } catch (IOException e) {
      throw new OpenAiChatException("sse send failed", e);
    }
  }

  private void sendDone(SseEmitter emitter, Map<String, Object> data) throws IOException {
    emitter.send(SseEmitter.event().name("done").data(data == null ? Map.of() : data));
  }

  private void safeDoneError(SseEmitter emitter, String error) {
    try {
      sendDone(emitter, Map.of("error", error == null ? "" : error));
      emitter.complete();
    } catch (Exception e) {
      emitter.completeWithError(e);
    }
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o).trim();
  }
}
