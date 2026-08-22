package com.bluedock.auth.service;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.ldap.LdapAuthenticator;
import com.bluedock.auth.ldap.LdapUserInfo;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.auth.web.dto.NeedCodeView;
import com.bluedock.auth.web.dto.RefreshResult;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.auth.LoginDeviceHook;
import com.bluedock.common.auth.RegPolicy;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  /** 连续失败 ≥3 次强制验证码。 */
  static final int CAPTCHA_FAIL_THRESHOLD = 3;

  private static final Duration FAIL_TTL = Duration.ofMinutes(15);

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokens;
  private final WirePasswordResolver passwords;
  private final CaptchaService captcha;
  private final StringRedisTemplate redis;
  private final LdapAuthenticator ldap;
  private final LoginDeviceHook deviceHook;
  private final ObjectProvider<LicenseCapacity> licenseCapacity;
  private final ObjectProvider<RegPolicy> regPolicy;

  public AuthService(
      UserAccountRepository users,
      PasswordEncoder passwordEncoder,
      TokenService tokens,
      WirePasswordResolver passwords,
      CaptchaService captcha,
      StringRedisTemplate redis,
      @Autowired(required = false) LdapAuthenticator ldap,
      @Autowired(required = false) LoginDeviceHook deviceHook,
      ObjectProvider<LicenseCapacity> licenseCapacity,
      ObjectProvider<RegPolicy> regPolicy) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.passwords = passwords;
    this.captcha = captcha;
    this.redis = redis;
    this.ldap = ldap;
    this.deviceHook = deviceHook;
    this.licenseCapacity = licenseCapacity;
    this.regPolicy = regPolicy;
  }

  public NeedCodeView needCode(String clientIp) {
    return new NeedCodeView(getLoginFailCount(clientIp) >= CAPTCHA_FAIL_THRESHOLD);
  }

  public NeedCodeView needInvite() {
    RegPolicy policy = regPolicy.getIfAvailable();
    return new NeedCodeView(policy != null && policy.needInvite());
  }

  @Transactional
  public LoginResult login(
      String email,
      String passwordCipher,
      String keyId,
      String clientIp,
      String userAgent,
      String captchaKey,
      String captchaCode) {
    if (email == null || email.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_EMPTY_CREDENTIALS);
    }
    String plainPassword = passwords.requirePlain(keyId, passwordCipher);
    enforceCaptchaIfNeeded(clientIp, captchaKey, captchaCode);

    String login = email.trim();
    Optional<UserAccount> local = users.findByEmail(login);

    if (local.isPresent()) {
      UserAccount user = local.get();
      assertLoginable(user);
      if (passwordEncoder.matches(plainPassword, user.getPassword())) {
        clearLoginFail(clientIp);
        maybeSyncLocalToLdap(user, plainPassword);
        return succeed(user, clientIp, userAgent);
      }
    }

    if (ldap != null && ldap.isEnabled()) {
      Optional<LdapUserInfo> info = ldap.authenticate(login, plainPassword);
      if (info.isPresent()) {
        UserAccount user = ensureLdapUser(info.get());
        assertLoginable(user);
        clearLoginFail(clientIp);
        return succeed(user, clientIp, userAgent);
      }
    }

    incrementLoginFail(clientIp);
    throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_FAILED);
  }

  /** 扫码确认前：账号存在且可登录。 */
  public void assertCanLogin(long userId) {
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.AUTH_USER_GONE));
    assertLoginable(user);
  }

  /** 扫码等免密登录：校验账号可登录后签发新 token（记设备）。 */
  public LoginResult loginByUserId(long userId, String clientIp, String userAgent) {
    assertCanLogin(userId);
    UserAccount user = users.findByUserId(userId).orElseThrow();
    return succeed(user, clientIp, userAgent);
  }

  public UserPublicView currentProfile() {
    long userId = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.AUTH_USER_GONE));
    if (user.getDisableAt() != null) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_DISABLED);
    }
    return UserPublicView.from(user);
  }

  public void logout(String token) {
    tokens.revoke(token);
  }

  /** 当前 Bearer token 剩余有效期；{@code expireAt} 为 UTC 毫秒时间戳。 */
  public Map<String, Object> tokenExpire(String token) {
    AuthContext.requireUserId();
    long ttlSeconds =
        tokens
            .remainingTtlSeconds(token)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.AUTH_USER_GONE));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ttlSeconds", ttlSeconds);
    out.put("expireAt", Instant.now().toEpochMilli() + ttlSeconds * 1000L);
    return out;
  }

  private void enforceCaptchaIfNeeded(String clientIp, String captchaKey, String captchaCode) {
    if (getLoginFailCount(clientIp) < CAPTCHA_FAIL_THRESHOLD) {
      return;
    }
    if (captchaKey == null
        || captchaKey.isBlank()
        || captchaCode == null
        || captchaCode.isBlank()) {
      throw new BusinessException(ErrorCodes.CAPTCHA_REQUIRED, I18nKeys.AUTH_CAPTCHA_REQUIRED);
    }
    if (!captcha.verifyAndConsume(captchaKey, captchaCode)) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_CAPTCHA_INVALID);
    }
  }

  private int getLoginFailCount(String clientIp) {
    String ip = normalizeIp(clientIp);
    String raw = redis.opsForValue().get(RedisKeys.loginFail(ip));
    if (raw == null || raw.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private void incrementLoginFail(String clientIp) {
    String key = RedisKeys.loginFail(normalizeIp(clientIp));
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, FAIL_TTL);
    }
  }

  private void clearLoginFail(String clientIp) {
    redis.delete(RedisKeys.loginFail(normalizeIp(clientIp)));
  }

  private static String normalizeIp(String clientIp) {
    return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
  }

  private LoginResult succeed(UserAccount user, String clientIp, String userAgent) {
    users.touchLogin(user.getUserId(), clientIp);
    TokenService.TokenPair pair = tokens.issuePair(user.getUserId());
    if (deviceHook != null) {
      deviceHook.onLogin(user.getUserId(), pair.accessToken(), userAgent, clientIp);
    }
    return new LoginResult(pair.accessToken(), pair.refreshToken(), UserPublicView.from(user));
  }

  /** refreshToken 轮换签发新 access/refresh；失败抛 {@link ErrorCodes#TOKEN_EXPIRED}。 */
  public RefreshResult refresh(String refreshToken) {
    TokenService.TokenPair pair = tokens.refresh(refreshToken);
    return new RefreshResult(pair.accessToken(), pair.refreshToken());
  }

  private void assertLoginable(UserAccount user) {
    if (user.getDisableAt() != null) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_DISABLED);
    }
    if (user.getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_BOT_LOGIN);
    }
    RegPolicy policy = regPolicy.getIfAvailable();
    if (policy != null && policy.isRegVerifyOpen() && user.getEmailVerify() != 1) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_EMAIL_UNVERIFIED);
    }
  }

  private UserAccount ensureLdapUser(LdapUserInfo info) {
    String email = info.email() == null ? "" : info.email().trim().toLowerCase();
    if (email.isEmpty()) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.LDAP_EMAIL_MISSING);
    }
    String nick =
        info.nickname() == null || info.nickname().isBlank()
            ? email.split("@")[0]
            : info.nickname().trim();
    Optional<UserAccount> existing = users.findByEmail(email);
    if (existing.isPresent()) {
      UserAccount user = existing.get();
      String merged = withLdapIdentity(user.getIdentity());
      if (!merged.equals(user.getIdentity())) {
        users.updateIdentity(user.getUserId(), merged);
        user.setIdentity(merged);
      }
      if (!nick.equals(nullToEmpty(user.getNickname()))) {
        user.setNickname(nick);
        users.updateProfile(user);
      }
      return user;
    }
    UserAccount created = new UserAccount();
    created.setUserId(IdGenerator.nextId());
    created.setEmail(email);
    created.setNickname(nick);
    created.setIdentity("[\"ldap\"]");
    created.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
    created.setIsBot(0);
    created.setUserImage("");
    created.setEmailVerify(1);
    created.setMustChangePassword(0);
    LicenseCapacity cap = licenseCapacity.getIfAvailable();
    if (cap != null) {
      cap.assertCanAddUser();
    }
    users.insert(created);
    return created;
  }

  /** 本地密码登录成功后：按需反向写入 LDAP。 */
  private void maybeSyncLocalToLdap(UserAccount user, String plainPassword) {
    if (ldap == null || !ldap.isEnabled()) {
      return;
    }
    if (hasLdapIdentity(user.getIdentity())) {
      return;
    }
    String email = user.getEmail() == null ? "" : user.getEmail().trim();
    if (email.isEmpty()) {
      return;
    }
    boolean created =
        ldap.syncLocalUser(email, nullToEmpty(user.getNickname()), plainPassword);
    if (!created) {
      return;
    }
    String merged = withLdapIdentity(user.getIdentity());
    if (!merged.equals(user.getIdentity())) {
      users.updateIdentity(user.getUserId(), merged);
      user.setIdentity(merged);
    }
  }

  static boolean hasLdapIdentity(String identity) {
    return identity != null
        && (identity.contains("\"ldap\"") || identity.contains("'ldap'"));
  }

  static String withLdapIdentity(String identity) {
    if (identity == null || identity.isBlank()) {
      return "[\"ldap\"]";
    }
    if (hasLdapIdentity(identity)) {
      return identity;
    }
    String trimmed = identity.trim();
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      String inner = trimmed.substring(1, trimmed.length() - 1).trim();
      if (inner.isEmpty()) {
        return "[\"ldap\"]";
      }
      return "[" + inner + ",\"ldap\"]";
    }
    return "[\"ldap\"]";
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
