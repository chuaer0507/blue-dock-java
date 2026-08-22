package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * AI 机器人 / 模型 Key 系统设置：{@code GET|POST /api/system/setting/aiBot*}。
 *
 * <p>与 {@link OssSettingService} / {@link EmailSettingService} 同属管理员可配：存 {@code
 * bluedock_settings.name=aiBotSetting}；GET 掩码 {@code *Key}/{@code *Secret}；POST 空或掩码保留原密文。普通用户经
 * {@code /api/assistant/models} 仅见 {@code *Models}/{@code *Model}。
 */
@Service
public class AiBotSettingService {
  public static final String SETTING_NAME = "aiBotSetting";
  public static final String SECRET_MASK = "********";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public AiBotSettingService(
      SettingRepository settings,
      ObjectMapper objectMapper,
      AdminGuard adminGuard,
      SettingWriteGuard writeGuard) {
    this.settings = settings;
    this.objectMapper = objectMapper;
    this.adminGuard = adminGuard;
    this.writeGuard = writeGuard;
  }

  public Map<String, Object> get() {
    adminGuard.requireAdmin();
    return maskSecrets(load());
  }

  public Map<String, Object> save(Map<String, Object> body) {
    adminGuard.requireAdmin();
    writeGuard.requireWritable();
    Map<String, Object> merged = mergeIncoming(load(), body);
    try {
      settings.upsert(SETTING_NAME, objectMapper.writeValueAsString(merged));
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_SETTING_INVALID);
    }
    return maskSecrets(merged);
  }

  public List<Map<String, Object>> models() {
    adminGuard.requireAdmin();
    Object v = load().get("models");
    if (v instanceof List<?> list) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object o : list) {
        if (o instanceof Map<?, ?> m) {
          Map<String, Object> row = new LinkedHashMap<>();
          for (Map.Entry<?, ?> e : m.entrySet()) {
            row.put(String.valueOf(e.getKey()), e.getValue());
          }
          out.add(row);
        }
      }
      return out;
    }
    return List.of();
  }

  public List<Map<String, Object>> defModels() {
    adminGuard.requireAdmin();
    return defaultModels();
  }

  /** 运行时读原文（不掩码、不鉴权）；供 assistant / AI 进程侧按需使用。 */
  public Map<String, Object> loadRaw() {
    return load();
  }

  /** 合并写请求：密钥字段空或掩码则保留库中原值。 */
  static Map<String, Object> mergeIncoming(Map<String, Object> current, Map<String, Object> body) {
    Map<String, Object> out = new LinkedHashMap<>(defaults());
    if (current != null) {
      out.putAll(current);
    }
    Map<String, Object> oldSecrets = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : out.entrySet()) {
      if (isSecretField(e.getKey())) {
        oldSecrets.put(e.getKey(), e.getValue());
      }
    }
    if (body != null) {
      out.putAll(body);
    }
    for (Map.Entry<String, Object> e : oldSecrets.entrySet()) {
      if (isMaskedOrBlank(out.get(e.getKey()))) {
        out.put(e.getKey(), e.getValue());
      }
    }
    // 新密钥字段：body 带了旧库没有的 secret key
    if (body != null) {
      for (Map.Entry<String, Object> e : body.entrySet()) {
        if (isSecretField(e.getKey()) && isMaskedOrBlank(e.getValue()) && !oldSecrets.containsKey(e.getKey())) {
          out.put(e.getKey(), "");
        }
      }
    }
    return out;
  }

  static Map<String, Object> maskSecrets(Map<String, Object> raw) {
    Map<String, Object> m = new LinkedHashMap<>(raw == null ? Map.of() : raw);
    for (Map.Entry<String, Object> e : m.entrySet()) {
      if (isSecretField(e.getKey()) && !str(e.getValue()).isBlank()) {
        e.setValue(SECRET_MASK);
      }
    }
    return m;
  }

  /** {@code apiKey}/{@code openaiKey}/{@code *Secret} 等视为密钥。 */
  static boolean isSecretField(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    String k = key.trim();
    return k.endsWith("Key") || k.endsWith("Secret");
  }

  static boolean isMaskedOrBlank(Object v) {
    String s = str(v);
    if (s.isBlank() || SECRET_MASK.equals(s)) {
      return true;
    }
    return s.contains("****");
  }

  private Map<String, Object> load() {
    Map<String, Object> out = defaults();
    settings
        .findSettingJson(SETTING_NAME)
        .ifPresent(
            json -> {
              try {
                out.putAll(
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
                normalizeLegacyKeys(out);
              } catch (Exception ignored) {
                // keep defaults
              }
            });
    return out;
  }

  static Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("open", "close");
    m.put("provider", "");
    m.put("apiKey", "");
    m.put("baseUrl", "");
    m.put("model", "");
    m.put("models", List.of());
    m.put("systemPrompt", "");
    // 多供应商 Key / 可见模型（assistant/models 只暴露 *Models / *Model）
    m.put("openaiKey", "");
    m.put("openaiModels", List.of());
    m.put("claudeKey", "");
    m.put("claudeModels", List.of());
    m.put("deepseekKey", "");
    m.put("deepseekModels", List.of());
    m.put("aiGatewayKey", "");
    return m;
  }

  private static List<Map<String, Object>> defaultModels() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(Map.of("id", "gpt-4o-mini", "name", "GPT-4o mini", "provider", "openai"));
    list.add(Map.of("id", "deepseek-chat", "name", "DeepSeek Chat", "provider", "deepseek"));
    return list;
  }

  /** 0.x：旧库 snake 供应商字段迁到 camelCase 后删除旧键。 */
  public static void normalizeLegacyKeys(Map<String, Object> m) {
    if (m == null) {
      return;
    }
    remap(m, "openai_key", "openaiKey");
    remap(m, "openai_models", "openaiModels");
    remap(m, "claude_key", "claudeKey");
    remap(m, "claude_models", "claudeModels");
    remap(m, "deepseek_key", "deepseekKey");
    remap(m, "deepseek_models", "deepseekModels");
  }

  private static void remap(Map<String, Object> m, String from, String to) {
    if (!m.containsKey(from)) {
      return;
    }
    Object v = m.remove(from);
    if (!m.containsKey(to) || isUnset(m.get(to))) {
      m.put(to, v);
    }
  }

  /** 目标键缺失、null、空白字符串或空集合时视为可被旧键覆盖。 */
  private static boolean isUnset(Object v) {
    if (v == null) {
      return true;
    }
    if (v instanceof String s) {
      return s.isBlank();
    }
    if (v instanceof java.util.Collection<?> c) {
      return c.isEmpty();
    }
    return str(v).isBlank();
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
