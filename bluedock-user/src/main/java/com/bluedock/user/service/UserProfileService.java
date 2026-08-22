package com.bluedock.user.service;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.ldap.LdapAuthenticator;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.user.web.dto.UserExtraView;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
  private static final Pattern BIRTHDAY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final int PASS_MIN = 6;
  private static final int PASS_MAX = 32;

  private final UserAccountRepository users;
  private final WirePasswordResolver passwords;
  private final PasswordEncoder passwordEncoder;
  private final LdapAuthenticator ldap;

  public UserProfileService(
      UserAccountRepository users,
      WirePasswordResolver passwords,
      PasswordEncoder passwordEncoder,
      @Autowired(required = false) LdapAuthenticator ldap) {
    this.users = users;
    this.passwords = passwords;
    this.passwordEncoder = passwordEncoder;
    this.ldap = ldap;
  }

  public UserPublicView basic(long userId) {
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (user.getDisableAt() != null || user.getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }
    return UserPublicView.from(user);
  }

  /** 扩展资料；{@code userId} 空则当前登录用户（可看自己的 bot/离职态以外字段）。 */
  public UserExtraView extra(Long userId) {
    long targetId = userId == null || userId <= 0 ? AuthContext.requireUserId() : userId;
    long me = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(targetId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (targetId != me && (user.getDisableAt() != null || user.getIsBot() == 1)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }
    return UserExtraView.from(user);
  }

  @Transactional
  public UserPublicView editData(
      String nickname,
      String userImage,
      String profession,
      String telephone,
      String birthday,
      String address,
      String introduction,
      String lang) {
    long userId = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.AUTH_USER_GONE));
    if (user.getDisableAt() != null) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_DISABLED);
    }

    if (nickname != null) {
      String n = nickname.trim();
      if (n.length() < 2 || n.length() > 20) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_NICKNAME_LENGTH);
      }
      user.setNickname(n);
    }
    if (userImage != null) {
      user.setUserImage(userImage.trim());
    }
    if (profession != null) {
      String p = profession.trim();
      if (!p.isEmpty() && (p.length() < 2 || p.length() > 20)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PROFESSION_LENGTH);
      }
      user.setProfession(p);
    }
    if (telephone != null) {
      String t = telephone.trim();
      if (!t.isEmpty() && (t.length() < 6 || t.length() > 20)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TEL_LENGTH);
      }
      if (!t.isEmpty() && users.existsTelephoneExcept(t, userId)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_TEL_TAKEN);
      }
      user.setTelephone(t);
    }
    if (birthday != null) {
      String b = birthday.trim();
      if (!b.isEmpty() && !BIRTHDAY.matcher(b).matches()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_BIRTHDAY_FORMAT);
      }
      user.setBirthday(b);
    }
    if (address != null) {
      String a = address.trim();
      if (a.length() > 100) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_ADDRESS_LENGTH);
      }
      user.setAddress(a);
    }
    if (introduction != null) {
      String i = introduction.trim();
      if (i.length() > 500) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_INTRO_LENGTH);
      }
      user.setIntroduction(i);
    }
    if (lang != null) {
      if (!Messages.isSupportedUserLang(lang)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_LANG_INVALID);
      }
      user.setLang(Messages.toUserLang(lang));
    }

    users.updateProfile(user);
    return UserPublicView.from(user);
  }

  /**
   * 修改自己的密码：旧/新密码均为 RSA 密文 + 同一 {@code keyId}；LDAP 用户回写目录。
   *
   * @param oldPasswordCipher 旧密码密文（参数名 {@code oldPassword}）
   * @param passwordCipher 新密码密文（参数名 {@code password}）
   */
  @Transactional
  public UserPublicView editPassword(String oldPasswordCipher, String passwordCipher, String keyId) {
    long userId = AuthContext.requireUserId();
    UserAccount user =
        users
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.AUTH_USER_GONE));
    if (user.getDisableAt() != null) {
      throw new BusinessException(ErrorCodes.AUTH_FAILED, I18nKeys.AUTH_DISABLED);
    }
    if (hasIdentity(user.getIdentity(), "system")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_SYSTEM_DENIED);
    }
    String oldPlain = passwords.requirePlain(keyId, oldPasswordCipher);
    String newPlain = passwords.requirePlain(keyId, passwordCipher);
    if (oldPlain.equals(newPlain)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_SAME);
    }
    if (newPlain.length() < PASS_MIN || newPlain.length() > PASS_MAX) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_LENGTH);
    }
    if (!verifyOldPassword(user, oldPlain)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.USER_PASS_OLD_INVALID);
    }
    boolean ldapUser = hasIdentity(user.getIdentity(), "ldap");
    if (ldapUser && ldap != null && ldap.isEnabled()) {
      String email = user.getEmail() == null ? "" : user.getEmail().trim();
      if (email.isEmpty() || !ldap.updatePassword(email, newPlain)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LDAP_PASS_WRITEBACK);
      }
    }
    users.updatePassword(userId, passwordEncoder.encode(newPlain));
    user.setPassword(null);
    return UserPublicView.from(user);
  }

  private boolean verifyOldPassword(UserAccount user, String oldPlain) {
    String hash = user.getPassword();
    if (hash != null && !hash.isBlank() && passwordEncoder.matches(oldPlain, hash)) {
      return true;
    }
    if (hasIdentity(user.getIdentity(), "ldap") && ldap != null && ldap.isEnabled()) {
      String email = user.getEmail() == null ? "" : user.getEmail().trim();
      if (!email.isEmpty()) {
        return ldap.authenticate(email, oldPlain).isPresent();
      }
    }
    return false;
  }

  static boolean hasIdentity(String identity, String tag) {
    if (identity == null || tag == null || tag.isBlank()) {
      return false;
    }
    String q = "\"" + tag + "\"";
    String sq = "'" + tag + "'";
    return identity.contains(q) || identity.contains(sq) || identity.contains("," + tag + ",");
  }
}
