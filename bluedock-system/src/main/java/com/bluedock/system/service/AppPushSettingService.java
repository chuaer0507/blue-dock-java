package com.bluedock.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.common.notify.apppush.AppPushSettingMaps;
import com.bluedock.system.repo.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * APP 推送系统设置：{@code GET|POST /api/system/setting/appPush}。
 *
 * <p>与 OSS / SMTP / aibot / meeting 同属管理员可配；GET 掩码 Key/Secret；POST 空或 {@code ********}
 * 保留原密文。
 */
@Service
public class AppPushSettingService {
  public static final String SETTING_NAME = NotifySettingNames.APP_PUSH;
  public static final String SECRET_MASK = "********";
  private static final Set<String> SECRET_FIELDS =
      Set.of("iosKey", "iosSecret", "androidKey", "androidSecret");

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public AppPushSettingService(
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
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.APP_PUSH_CONFIG);
    }
    return maskSecrets(merged);
  }

  /** 运行时读原文（不掩码）。 */
  public Map<String, Object> loadRaw() {
    return load();
  }

  public boolean enabled() {
    return AppPushSettingMaps.enabled(load());
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
    settings
        .findSettingJson(SETTING_NAME)
        .ifPresent(
            json -> {
              try {
                out.putAll(
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
              } catch (Exception ignored) {
                // keep defaults
              }
            });
    return out;
  }

  static Map<String, Object> defaults() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("open", "close");
    m.put("iosKey", "");
    m.put("iosSecret", "");
    m.put("androidKey", "");
    m.put("androidSecret", "");
    m.put("aliasType", "bluedock");
    m.put("productionMode", "true");
    return m;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
