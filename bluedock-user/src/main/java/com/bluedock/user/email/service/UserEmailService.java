package com.bluedock.user.email.service;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.mail.SmtpMailClient;
import com.bluedock.common.notify.mail.SmtpMailClient.SmtpConfig;
import com.bluedock.system.service.EmailSettingService;
import com.bluedock.user.email.repo.UserEmailVerificationRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 邮箱验证 / 改邮：链接码 30 分钟有效。 */
@Service
public class UserEmailService {
  public static final String TYPE_REG = "reg";
  public static final String TYPE_EDIT = "edit";
  public static final String TYPE_DELETE = "delete";
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final int EMAIL_MAX = 32;

  private final UserEmailVerificationRepository verifications;
  private final UserAccountRepository users;
  private final EmailSettingService emailSettings;
  private final String publicBaseUrl;

  public UserEmailService(
      UserEmailVerificationRepository verifications,
      UserAccountRepository users,
      EmailSettingService emailSettings,
      @Value("${bluedock.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    this.verifications = verifications;
    this.users = users;
    this.emailSettings = emailSettings;
    this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/$", "");
  }

  /** 向当前用户邮箱重发注册验证链接。 */
  @Transactional
  public Map<String, Object> send() {
    long userId = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (user.getEmailVerify() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_ALREADY_VERIFIED);
    }
    return issueAndSend(userId, user.getEmail(), TYPE_REG, user.getNickname());
  }

  /** 申请改邮箱：向新地址发验证链接，确认后更新。 */
  @Transactional
  public Map<String, Object> edit(String emailRaw) {
    long userId = AuthContext.requireUserId();
    String email = normalizeEmail(emailRaw);
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmail(email)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_TAKEN);
    }
    return issueAndSend(userId, email, TYPE_EDIT, user.getNickname());
  }

  /** 注销确认：校验当前用户 pending 的 delete 类型验证码并标记已用。 */
  @Transactional
  public void consumeDeleteCode(long userId, String codeRaw) {
    if (codeRaw == null || codeRaw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_REQUIRED);
    }
    String code = codeRaw.trim();
    Map<String, Object> row =
        verifications
            .findByCode(code)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_INVALID));
    int status = ((Number) row.get("status")).intValue();
    if (status == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_USED);
    }
    LocalDateTime created = toLocalDateTime(row.get("createdAt"));
    if (created == null || created.isBefore(LocalDateTime.now().minusMinutes(30))) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_EXPIRED);
    }
    long owner = ((Number) row.get("userId")).longValue();
    String type = String.valueOf(row.get("type"));
    if (owner != userId || !TYPE_DELETE.equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_INVALID);
    }
    verifications.markUsed(((Number) row.get("id")).longValue());
  }

  /** 匿名确认验证码。 */
  @Transactional
  public Map<String, Object> verify(String codeRaw) {
    if (codeRaw == null || codeRaw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_REQUIRED);
    }
    String code = codeRaw.trim();
    Map<String, Object> row =
        verifications
            .findByCode(code)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_INVALID));
    int status = ((Number) row.get("status")).intValue();
    if (status == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_USED);
    }
    LocalDateTime created = toLocalDateTime(row.get("createdAt"));
    if (created == null || created.isBefore(LocalDateTime.now().minusMinutes(30))) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_CODE_EXPIRED);
    }
    long userId = ((Number) row.get("userId")).longValue();
    String email = String.valueOf(row.get("email"));
    String type = String.valueOf(row.get("type"));
    verifications.markUsed(((Number) row.get("id")).longValue());
    if (TYPE_EDIT.equals(type)) {
      users
          .findByEmail(email)
          .ifPresent(
              other -> {
                if (other.getUserId() != userId) {
                  throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_TAKEN);
                }
              });
      users.updateEmail(userId, email);
    } else {
      users.updateEmailVerify(userId, 1);
    }
    return Map.of("ok", true, "type", type);
  }

  private Map<String, Object> issueAndSend(
      long userId, String email, String type, String nickname) {
    LocalDateTime after = LocalDateTime.now().minusMinutes(30);
    if (verifications.findRecentPending(userId, after).isPresent()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_SEND_COOLDOWN);
    }
    verifications.deleteByUserId(userId);
    String code =
        (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    verifications.insert(userId, email, code, type);
    String url = publicBaseUrl + "/single/valid/email?code=" + code;
    String subject = "邮箱验证";
    String body =
        (nickname == null || nickname.isBlank() ? "您好" : nickname)
            + "，请于 30 分钟内打开以下链接完成邮箱验证：\n"
            + url;
    SmtpConfig cfg = emailSettings.smtpConfig();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sent", true);
    out.put("email", email);
    if (!cfg.configured()) {
      out.put("devCode", code);
      return out;
    }
    try {
      SmtpMailClient.send(cfg, email, subject, body);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_SEND_FAILED);
    }
    return out;
  }

  private static String normalizeEmail(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_INVALID);
    }
    String email = raw.trim().toLowerCase(Locale.ROOT);
    if (email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_INVALID);
    }
    return email;
  }

  private static LocalDateTime toLocalDateTime(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof LocalDateTime ldt) {
      return ldt;
    }
    if (v instanceof Timestamp ts) {
      return ts.toLocalDateTime();
    }
    if (v instanceof java.util.Date d) {
      return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneOffset.UTC);
    }
    return null;
  }
}
