package com.bluedock.system.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.common.notify.mail.EmailSettingMaps;
import com.bluedock.common.notify.mail.SmtpMailClient;
import com.bluedock.common.notify.mail.SmtpMailClient.SmtpConfig;
import com.bluedock.system.repo.SettingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 邮件 SMTP 系统设置：{@code GET|POST /api/system/setting/email}、{@code GET /api/system/email/check}。
 *
 * <p>与 {@link OssSettingService} / {@link FileSettingService} 同属管理员可配项：存 {@code
 * bluedock_settings.name=emailSetting}；GET 掩码密码；POST 空或 {@code ********} 保留原密文。
 */
@Service
public class EmailSettingService {
  public static final String SETTING_NAME = NotifySettingNames.EMAIL;
  public static final String SECRET_MASK = "********";

  private final SettingRepository settings;
  private final ObjectMapper objectMapper;
  private final AdminGuard adminGuard;
  private final SettingWriteGuard writeGuard;

  public EmailSettingService(
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
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_CONFIG);
    }
    return maskSecrets(merged);
  }

  /** 管理员测试发信（同步，不经 Kafka）。 */
  public Map<String, Object> check(String toEmail) {
    adminGuard.requireAdmin();
    if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_ADDR_INVALID);
    }
    SmtpConfig cfg = EmailSettingMaps.toSmtp(load());
    if (!cfg.configured()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_CONFIG);
    }
    try {
      SmtpMailClient.send(cfg, toEmail.trim(), "BlueDock mail test", "SMTP configuration OK.");
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_SEND_FAILED);
    }
    return Map.of("ok", true, "to", toEmail.trim());
  }

  public SmtpConfig smtpConfig() {
    return EmailSettingMaps.toSmtp(load());
  }

  public List<String> ignoreAddresses() {
    return EmailSettingMaps.parseIgnore(load());
  }

  /** 业务侧读配置（无需管理员）；含 SMTP 与未读汇总开关。 */
  public Map<String, Object> loadRaw() {
    return load();
  }

  /** 合并写请求：密码空或掩码则保留库中原值。 */
  static Map<String, Object> mergeIncoming(Map<String, Object> current, Map<String, Object> body) {
    Map<String, Object> out = new LinkedHashMap<>(defaults());
    if (current != null) {
      out.putAll(current);
    }
    String oldPwd = str(out.get("smtpPassword"));
    if (body != null) {
      out.putAll(body);
    }
    String newPwd = str(out.get("smtpPassword"));
    if (newPwd.isBlank() || SECRET_MASK.equals(newPwd)) {
      out.put("smtpPassword", oldPwd);
    }
    return out;
  }

  static Map<String, Object> maskSecrets(Map<String, Object> raw) {
    Map<String, Object> m = new LinkedHashMap<>(raw == null ? Map.of() : raw);
    Object pwd = m.get("smtpPassword");
    if (pwd != null && !str(pwd).isBlank()) {
      m.put("smtpPassword", SECRET_MASK);
    }
    return m;
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
    m.put("smtpHost", "");
    m.put("smtpPort", "465");
    m.put("smtpUsername", "");
    m.put("smtpPassword", "");
    m.put("smtpSsl", "open");
    m.put("fromAlias", "BlueDock");
    m.put("fromAddress", "");
    m.put("ignoreAddr", "");
    m.put("regVerify", "close");
    m.put("noticeMessage", "close");
    m.put(
        "messageUnreadTimeRanges",
        List.of(List.of("00:00", "09:00"), List.of("18:00", "23:59")));
    m.put("messageUnreadUserMinute", 30);
    m.put("messageUnreadGroupMinute", 60);
    return m;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }
}
