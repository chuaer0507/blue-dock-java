package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.repo.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 会议（Agora）系统设置：{@code GET|POST /api/system/setting/meeting}。
 *
 * <p>与 OSS / SMTP / aibot 同属管理员可配；GET 掩码证书与密钥；POST 空或 {@code ********} 保留原密文。运行时经
 * {@link #loadRaw()} 读原文。
 */
@Service
public class MeetingSettingService {
  public static final String SETTING_NAME = "meetingSetting";
  public static final String SECRET_MASK = "********";
  private static final Set<String> SECRET_FIELDS =
      Set.of("appCertificate", "apiKey", "apiSecret", "channelSalt");

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public MeetingSettingService(
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
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.MEETING_CONFIG);
    }
    return maskSecrets(merged);
  }

  /** 运行时合并用：无落库配置时返回空 Map，调用方继续用 YAML。不掩码。 */
  public Map<String, Object> loadRaw() {
    return settings
        .findSettingJson(SETTING_NAME)
        .map(
            json -> {
              try {
                return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
              } catch (Exception e) {
                return Map.<String, Object>of();
              }
            })
        .orElse(Map.of());
  }

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

  static boolean isSecretField(String key) {
    return key != null && SECRET_FIELDS.contains(key.trim());
  }

  static boolean isMaskedOrBlank(Object v) {
    String s = str(v);
    return s.isBlank() || SECRET_MASK.equals(s) || s.contains("****");
  }

  private Map<String, Object> load() {
    Map<String, Object> out = defaults();
    out.putAll(loadRaw());
    return out;
  }

  static Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", "open");
    m.put("appId", "");
    m.put("appCertificate", "");
    m.put("apiKey", "");
    m.put("apiSecret", "");
    m.put("allowDevToken", "open");
    m.put("allowCloseWithoutRest", "close");
    m.put("closeIdleMinutes", 10);
    m.put("channelSalt", "");
    m.put("shareBaseUrl", "");
    m.put("shareTtlHours", 6);
    return m;
  }

  public static boolean openFlag(Map<String, Object> cfg, String key, boolean def) {
    return openFlag(cfg, key, null, def);
  }

  public static boolean openFlag(Map<String, Object> cfg, String key, String alt, boolean def) {
    Object v = cfg == null ? null : cfg.get(key);
    if (v == null && cfg != null && alt != null) {
      v = cfg.get(alt);
    }
    if (v == null) {
      return def;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) {
      return def;
    }
    return "open".equals(s) || "true".equals(s) || "1".equals(s);
  }

  public static String str(Map<String, Object> cfg, String key) {
    if (cfg == null) {
      return "";
    }
    Object v = cfg.get(key);
    return v == null ? "" : String.valueOf(v).trim();
  }

  public static int intVal(Map<String, Object> cfg, String key, int def) {
    if (cfg == null) {
      return def;
    }
    Object v = cfg.get(key);
    if (v == null) {
      return def;
    }
    try {
      return Integer.parseInt(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return def;
    }
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
