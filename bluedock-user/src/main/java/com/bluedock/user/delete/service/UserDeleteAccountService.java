package com.bluedock.user.delete.service;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.service.TokenService;
import com.bluedock.common.auth.RegPolicy;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.user.delete.repo.UserDeleteRepository;
import com.bluedock.user.device.repo.UserDeviceRepository;
import com.bluedock.user.email.service.UserEmailService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注销账号：{@code type=warning} 预检，{@code type=confirm} 执行。
 *
 * <p>{@code regVerify=open} 时确认需邮箱验证码（type=delete）；否则 RSA {@code password}+{@code keyId}。
 */
@Service
public class UserDeleteAccountService {
  private final UserAccountRepository users;
  private final UserDeleteRepository deletes;
  private final UserDeviceRepository devices;
  private final TokenService tokens;
  private final WirePasswordResolver passwords;
  private final PasswordEncoder passwordEncoder;
  private final UserEmailService emails;
  private final ObjectProvider<RegPolicy> regPolicy;
  private final ObjectMapper objectMapper;

  public UserDeleteAccountService(
      UserAccountRepository users,
      UserDeleteRepository deletes,
      UserDeviceRepository devices,
      TokenService tokens,
      WirePasswordResolver passwords,
      PasswordEncoder passwordEncoder,
      UserEmailService emails,
      ObjectProvider<RegPolicy> regPolicy,
      ObjectMapper objectMapper) {
    this.users = users;
    this.deletes = deletes;
    this.devices = devices;
    this.tokens = tokens;
    this.passwords = passwords;
    this.passwordEncoder = passwordEncoder;
    this.emails = emails;
    this.regPolicy = regPolicy;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> handle(
      String typeRaw,
      String emailRaw,
      String reasonRaw,
      String passwordCipher,
      String keyId,
      String code) {
    String type =
        typeRaw == null || typeRaw.isBlank()
            ? "warning"
            : typeRaw.trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "warning" -> warning(emailRaw, reasonRaw);
      case "confirm" -> confirm(emailRaw, reasonRaw, passwordCipher, keyId, code);
      default -> throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_DELETE_TYPE_INVALID);
    };
  }

  private Map<String, Object> warning(String emailRaw, String reasonRaw) {
    UserAccount user = loadAndValidate(emailRaw, reasonRaw);
    boolean needCode = isRegVerifyOpen();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("needConfirm", true);
    out.put("needCode", needCode);
    out.put("email", user.getEmail());
    return out;
  }

  @Transactional
  public Map<String, Object> confirm(
      String emailRaw, String reasonRaw, String passwordCipher, String keyId, String code) {
    UserAccount user = loadAndValidate(emailRaw, reasonRaw);
    if (isRegVerifyOpen()) {
      emails.consumeDeleteCode(user.getUserId(), code);
    } else {
      String plain = passwords.requirePlain(keyId, passwordCipher);
      if (!passwordEncoder.matches(plain, user.getPassword())) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_OLD_INVALID);
      }
    }
    String cache;
    try {
      Map<String, Object> snap = new LinkedHashMap<>();
      snap.put("userId", user.getUserId());
      snap.put("email", user.getEmail());
      snap.put("nickname", user.getNickname());
      snap.put("identity", user.getIdentity());
      snap.put("profession", user.getProfession());
      snap.put("telephone", user.getTelephone());
      cache = objectMapper.writeValueAsString(snap);
    } catch (Exception e) {
      cache = "{}";
    }
    String reason = reasonRaw == null ? "" : reasonRaw.trim();
    deletes.insert(user.getUserId(), user.getEmail(), user.getNickname(), reason, cache);
    for (Map<String, Object> row : devices.listActive(user.getUserId(), 500)) {
      String hash = String.valueOf(row.get("hash"));
      tokens.revokeByHash(hash);
      devices.softDelete(((Number) row.get("id")).longValue());
    }
    users.deleteByUserId(user.getUserId());
    return Map.of("ok", true);
  }

  private UserAccount loadAndValidate(String emailRaw, String reasonRaw) {
    long userId = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (hasSystem(user.getIdentity())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_DELETE_SYSTEM_DENIED);
    }
    String email =
        emailRaw == null ? "" : emailRaw.trim().toLowerCase(Locale.ROOT);
    if (email.isEmpty() || !email.equalsIgnoreCase(user.getEmail())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_DELETE_EMAIL_MISMATCH);
    }
    String reason = reasonRaw == null ? "" : reasonRaw.trim();
    if (reason.isEmpty() || reason.length() > 500) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_DELETE_REASON_INVALID);
    }
    return user;
  }

  private boolean isRegVerifyOpen() {
    RegPolicy policy = regPolicy.getIfAvailable();
    return policy != null && policy.isRegVerifyOpen();
  }

  private static boolean hasSystem(String identity) {
    if (identity == null || identity.isBlank()) {
      return false;
    }
    String t = identity.toLowerCase(Locale.ROOT);
    return t.contains("\"system\"") || t.contains("system");
  }
}
