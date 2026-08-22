package com.bluedock.auth.service;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.auth.AuthMailBridge;
import com.bluedock.common.auth.RegPolicy;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自助注册 / 忘记密码：邮箱 OTP（Redis）+ RSA 密码。
 *
 * <p>路径：{@code users/email/code}、{@code users/register}、{@code users/password/reset}。
 */
@Service
public class RegisterService {
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Duration COOL_TTL = Duration.ofSeconds(60);
  private static final int PASS_MIN = 6;
  private static final int PASS_MAX = 32;
  private static final int EMAIL_MAX = 32;

  public static final String TYPE_REG = "reg";
  public static final String TYPE_RESET = "reset";

  private final UserAccountRepository users;
  private final WirePasswordResolver passwords;
  private final PasswordEncoder passwordEncoder;
  private final StringRedisTemplate redis;
  private final AuthService authService;
  private final ObjectProvider<RegPolicy> regPolicy;
  private final ObjectProvider<LicenseCapacity> licenseCapacity;
  private final AuthMailBridge mail;

  public RegisterService(
      UserAccountRepository users,
      WirePasswordResolver passwords,
      PasswordEncoder passwordEncoder,
      StringRedisTemplate redis,
      AuthService authService,
      ObjectProvider<RegPolicy> regPolicy,
      ObjectProvider<LicenseCapacity> licenseCapacity,
      @Autowired(required = false) AuthMailBridge mail) {
    this.users = users;
    this.passwords = passwords;
    this.passwordEncoder = passwordEncoder;
    this.redis = redis;
    this.authService = authService;
    this.regPolicy = regPolicy;
    this.licenseCapacity = licenseCapacity;
    this.mail = mail;
  }

  /** 发送邮箱验证码；SMTP 未配置时返回 {@code devCode}。 */
  public Map<String, Object> sendEmailCode(String emailRaw, String typeRaw) {
    String type = normalizeType(typeRaw);
    String email = normalizeEmail(emailRaw);
    if (TYPE_REG.equals(type)) {
      assertRegistrationAllowed();
      if (users.existsByEmail(email)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_TAKEN);
      }
    } else {
      if (users.findByEmail(email).isEmpty()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_RESET_USER_MISSING);
      }
    }

    String coolKey = RedisKeys.authEmailCodeCool(type, email);
    if (Boolean.TRUE.equals(redis.hasKey(coolKey))) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_EMAIL_CODE_COOLDOWN);
    }

    String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
    redis.opsForValue().set(RedisKeys.authEmailCode(type, email), code, CODE_TTL);
    redis.opsForValue().set(coolKey, "1", COOL_TTL);

    String subject =
        TYPE_REG.equals(type) ? "BlueDock registration code" : "BlueDock password reset code";
    String body = "Your verification code is: " + code + "\nValid for 10 minutes.";

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    out.put("email", email);
    out.put("expiresIn", CODE_TTL.toSeconds());

    if (mail == null || !sendSafe(email, subject, body)) {
      out.put("devCode", code);
    }
    return out;
  }

  @Transactional
  public Map<String, Object> register(
      String emailRaw,
      String passwordCipher,
      String keyId,
      String emailCode,
      String nicknameRaw,
      String invite,
      String clientIp,
      String userAgent) {
    assertRegistrationAllowed();
    RegPolicy policy = regPolicy.getIfAvailable();
    if (policy != null) {
      policy.assertInvite(invite);
    }

    String email = normalizeEmail(emailRaw);
    consumeEmailCode(TYPE_REG, email, emailCode);

    if (users.existsByEmail(email)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_EMAIL_TAKEN);
    }

    String nickname = nicknameRaw == null ? "" : nicknameRaw.trim();
    if (nickname.isEmpty()) {
      nickname = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
    }
    if (nickname.length() < 2 || nickname.length() > 20) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_NICKNAME_LENGTH);
    }

    String plain = passwords.requirePlain(keyId, passwordCipher);
    if (plain.length() < PASS_MIN || plain.length() > PASS_MAX) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_LENGTH);
    }

    LicenseCapacity cap = licenseCapacity.getIfAvailable();
    if (cap != null) {
      cap.assertCanAddUser();
    }

    UserAccount u = new UserAccount();
    u.setUserId(IdGenerator.nextId());
    u.setEmail(email);
    u.setNickname(nickname);
    u.setIdentity("[]");
    u.setPassword(passwordEncoder.encode(plain));
    u.setIsBot(0);
    u.setUserImage("");
    u.setProfession("");
    // OTP 已校验；若开启 regVerify 链接验证则仍置未验证
    boolean requireLink = policy != null && policy.isRegVerifyOpen();
    u.setEmailVerify(requireLink ? 0 : 1);
    u.setMustChangePassword(0);
    users.insert(u);

    Map<String, Object> out = new LinkedHashMap<>();
    if (requireLink) {
      out.put("requireEmailVerify", true);
      out.put("user", UserPublicView.from(u));
      return out;
    }

    LoginResult login = authService.loginByUserId(u.getUserId(), clientIp, userAgent);
    out.put("token", login.token());
    out.put("refreshToken", login.refreshToken());
    out.put("user", login.user());
    return out;
  }

  @Transactional
  public Map<String, Object> resetPassword(
      String emailRaw, String emailCode, String passwordCipher, String keyId) {
    String email = normalizeEmail(emailRaw);
    consumeEmailCode(TYPE_RESET, email, emailCode);

    UserAccount user =
        users
            .findByEmail(email)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_RESET_USER_MISSING));

    String plain = passwords.requirePlain(keyId, passwordCipher);
    if (plain.length() < PASS_MIN || plain.length() > PASS_MAX) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_LENGTH);
    }
    users.updatePassword(user.getUserId(), passwordEncoder.encode(plain));
    return Map.of("ok", true);
  }

  private void assertRegistrationAllowed() {
    RegPolicy policy = regPolicy.getIfAvailable();
    if (policy != null && policy.isRegistrationClosed()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_REGISTER_CLOSED);
    }
  }

  private void consumeEmailCode(String type, String email, String codeRaw) {
    String code = codeRaw == null ? "" : codeRaw.trim();
    if (code.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_EMAIL_CODE_INVALID);
    }
    String key = RedisKeys.authEmailCode(type, email);
    String expect = redis.opsForValue().get(key);
    if (expect == null || expect.isBlank() || !expect.equals(code)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_EMAIL_CODE_INVALID);
    }
    redis.delete(key);
  }

  private boolean sendSafe(String to, String subject, String body) {
    try {
      return mail.send(to, subject, body);
    } catch (RuntimeException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_SEND_FAILED);
    }
  }

  private static String normalizeType(String typeRaw) {
    String type = typeRaw == null ? "" : typeRaw.trim().toLowerCase(Locale.ROOT);
    if (!TYPE_REG.equals(type) && !TYPE_RESET.equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_EMAIL_CODE_TYPE);
    }
    return type;
  }

  private static String normalizeEmail(String emailRaw) {
    if (emailRaw == null || emailRaw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_EMPTY_CREDENTIALS);
    }
    String email = emailRaw.trim().toLowerCase(Locale.ROOT);
    if (email.length() > EMAIL_MAX || !EMAIL.matcher(email).matches()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.EMAIL_ADDR_INVALID);
    }
    return email;
  }
}
