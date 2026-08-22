package com.bluedock.system.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.ai.OpenAiChatException;
import com.bluedock.common.ai.OpenAiCompatibleChatClient;
import com.bluedock.system.service.AiBotSettingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从 {@code aiBotSetting} 解析凭证，调用 OpenAI 兼容 chat completions。
 *
 * <p>同步：报告 / 任务 AI；流式：助手 {@code /api/ai/invoke/stream/{streamKey}}。
 */
@Service
public class AiBotChatService {
  private static final Logger log = LoggerFactory.getLogger(AiBotChatService.class);

  private final AiBotSettingService settings;
  private final OpenAiCompatibleChatClient client;

  @org.springframework.beans.factory.annotation.Autowired
  public AiBotChatService(AiBotSettingService settings, ObjectMapper objectMapper) {
    this.settings = settings;
    this.client = new OpenAiCompatibleChatClient(objectMapper);
  }

  /** 测试注入。 */
  AiBotChatService(AiBotSettingService settings, OpenAiCompatibleChatClient client) {
    this.settings = settings;
    this.client = client;
  }

  public boolean available() {
    return resolveCred(null) != null;
  }

  /**
   * @return assistant 文本；不可用或失败返回 null（调用方决定是否降级）
   */
  public String chat(String systemPrompt, String userPrompt) {
    Cred cred = resolveCred(null);
    if (cred == null) {
      return null;
    }
    List<Map<String, String>> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(Map.of("role", "system", "content", systemPrompt.trim()));
    }
    messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
    try {
      return client.chatCompletions(cred.baseUrl(), cred.apiKey(), cred.model(), messages);
    } catch (OpenAiChatException e) {
      log.warn("aiBot chat failed: {}", e.toString());
      return null;
    }
  }

  /**
   * 流式对话；按片段回调。不可用抛 {@link OpenAiChatException}。
   *
   * @param modelOverride 非空时覆盖配置默认 model
   * @param messages OpenAI 风格 messages
   */
  public void chatStream(
      String modelOverride,
      List<Map<String, String>> messages,
      java.util.function.Consumer<String> onDelta) {
    Cred cred = resolveCred(modelOverride);
    if (cred == null) {
      throw new OpenAiChatException("aiBot unavailable");
    }
    try {
      client.chatCompletionsStream(
          cred.baseUrl(), cred.apiKey(), cred.model(), messages, onDelta);
    } catch (OpenAiChatException streamEx) {
      log.warn("aiBot stream failed, fallback sync: {}", streamEx.toString());
      String full =
          client.chatCompletions(cred.baseUrl(), cred.apiKey(), cred.model(), messages);
      if (full != null && !full.isBlank()) {
        onDelta.accept(full);
      }
    }
  }

  /** 配置中的 systemPrompt（可空）。 */
  public String systemPrompt() {
    Map<String, Object> raw = settings.loadRaw();
    return str(raw.get("systemPrompt"));
  }

  /**
   * 批量 embedding；不可用或失败返回 null（调用方回退词法）。
   *
   * <p>模型优先 {@code embeddingModel}，否则 {@code text-embedding-3-small}（DeepSeek 网关可能不支持）。
   */
  public List<float[]> embed(List<String> inputs) {
    Cred cred = resolveCred(null);
    if (cred == null || inputs == null || inputs.isEmpty()) {
      return null;
    }
    Map<String, Object> raw = settings.loadRaw();
    String embModel = str(raw.get("embeddingModel"));
    if (embModel.isBlank()) {
      embModel = "text-embedding-3-small";
    }
    try {
      return client.embeddings(cred.baseUrl(), cred.apiKey(), embModel, inputs);
    } catch (OpenAiChatException e) {
      log.warn("aiBot embed failed: {}", e.toString());
      return null;
    }
  }

  /**
   * OpenAI 兼容语音转写；不可用或失败返回 null。
   *
   * @param prompt 可选上下文提示（如语言偏好、近期对话）
   */
  public String transcribe(byte[] audio, String filename, String contentType, String prompt) {
    Cred cred = resolveCred(null);
    if (cred == null) {
      return null;
    }
    try {
      return client.audioTranscriptions(
          cred.baseUrl(),
          cred.apiKey(),
          TRANSCRIBE_MODEL,
          audio,
          filename,
          contentType,
          prompt);
    } catch (OpenAiChatException e) {
      log.warn("aiBot transcribe failed: {}", e.toString());
      return null;
    }
  }

  private static final String TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe";

  Cred resolveCred(String modelOverride) {
    Map<String, Object> raw = settings.loadRaw();
    if (!isOpen(raw.get("open"))) {
      return null;
    }

    String provider = str(raw.get("provider")).toLowerCase(Locale.ROOT);
    String apiKey = str(raw.get("apiKey"));
    String baseUrl = str(raw.get("baseUrl"));
    String model = str(raw.get("model"));
    if (modelOverride != null && !modelOverride.isBlank()) {
      model = modelOverride.trim();
    }

    if (apiKey.isBlank()) {
      String openai = str(raw.get("openaiKey"));
      String deepseek = str(raw.get("deepseekKey"));
      String gateway = str(raw.get("aiGatewayKey"));
      if ("deepseek".equals(provider) || (openai.isBlank() && !deepseek.isBlank())) {
        apiKey = deepseek;
        if (baseUrl.isBlank()) {
          baseUrl = "https://api.deepseek.com";
        }
        if (model.isBlank()) {
          model = firstModelId(raw.get("deepseekModels"), "deepseek-chat");
        }
      } else if (!openai.isBlank()) {
        apiKey = openai;
        if (baseUrl.isBlank()) {
          baseUrl = "https://api.openai.com";
        }
        if (model.isBlank()) {
          model = firstModelId(raw.get("openaiModels"), "gpt-4o-mini");
        }
      } else if (!gateway.isBlank()) {
        apiKey = gateway;
      } else if ("claude".equals(provider)) {
        // Claude 官方协议非 OpenAI 兼容：仅当同时配置了 baseUrl（兼容网关）才启用
        apiKey = str(raw.get("claudeKey"));
        if (baseUrl.isBlank() || apiKey.isBlank()) {
          return null;
        }
        if (model.isBlank()) {
          model = firstModelId(raw.get("claudeModels"), "");
        }
      }
    }

    if (apiKey.isBlank()) {
      return null;
    }
    if (baseUrl.isBlank()) {
      baseUrl =
          "deepseek".equals(provider) ? "https://api.deepseek.com" : "https://api.openai.com";
    }
    if (model.isBlank()) {
      model =
          "deepseek".equals(provider)
              ? firstModelId(raw.get("deepseekModels"), "deepseek-chat")
              : firstModelId(raw.get("openaiModels"), "gpt-4o-mini");
    }
    if (model.isBlank()) {
      return null;
    }
    return new Cred(baseUrl, apiKey, model);
  }

  static boolean isOpen(Object open) {
    if (open instanceof Boolean b) {
      return b;
    }
    String s = str(open).toLowerCase(Locale.ROOT);
    return "open".equals(s) || "true".equals(s) || "1".equals(s) || "on".equals(s);
  }

  static String firstModelId(Object models, String fallback) {
    if (models instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Map<?, ?> m) {
          Object id = m.get("id");
          if (id == null) {
            id = m.get("model");
          }
          String s = str(id);
          if (!s.isBlank()) {
            return s;
          }
        } else if (o != null) {
          String s = str(o);
          if (!s.isBlank()) {
            return s;
          }
        }
      }
    }
    return fallback == null ? "" : fallback;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  record Cred(String baseUrl, String apiKey, String model) {}
}
