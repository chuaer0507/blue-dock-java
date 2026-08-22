package com.bluedock.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.ldap.LdapAuthenticator;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceTest {
  @Mock UserAccountRepository users;
  @Mock WirePasswordResolver passwords;
  @Mock PasswordEncoder passwordEncoder;
  @Mock LdapAuthenticator ldap;

  UserProfileService profiles;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
    profiles = new UserProfileService(users, passwords, passwordEncoder, ldap);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void editData_ok() {
    UserAccount u = baseUser();
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));

    UserPublicView view =
        profiles.editData("新昵称", null, "工程师", "13800138000", "1990-01-01", null, null, "zh-CN");

    assertEquals("新昵称", view.nickname());
    assertEquals("13800138000", view.telephone());
    verify(users).updateProfile(u);
  }

  @Test
  void editData_badNickname() {
    when(users.findByUserId(1L)).thenReturn(Optional.of(baseUser()));
    assertThrows(
        BusinessException.class,
        () -> profiles.editData("a", null, null, null, null, null, null, null));
  }

  @Test
  void basic_ok() {
    when(users.findByUserId(2L)).thenReturn(Optional.of(baseUser()));
    UserPublicView view = profiles.basic(2L);
    assertEquals("Admin", view.nickname());
  }

  @Test
  void extra_defaultsToSelf() {
    when(users.findByUserId(1L)).thenReturn(Optional.of(baseUser()));
    assertEquals(1L, profiles.extra(null).userId());
    assertEquals("admin@bluedock.local", profiles.extra(null).email());
  }

  @Test
  void extra_otherUser() {
    UserAccount other = baseUser();
    other.setUserId(2L);
    other.setEmail("u2@bluedock.local");
    when(users.findByUserId(2L)).thenReturn(Optional.of(other));
    assertEquals(2L, profiles.extra(2L).userId());
  }

  @Test
  void editPassword_ok() {
    UserAccount u = baseUser();
    u.setPassword("hash");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(passwords.requirePlain("k1", "oldCipher")).thenReturn("OldPass1");
    when(passwords.requirePlain("k1", "newCipher")).thenReturn("NewPass1");
    when(passwordEncoder.matches("OldPass1", "hash")).thenReturn(true);
    when(passwordEncoder.encode("NewPass1")).thenReturn("newHash");

    profiles.editPassword("oldCipher", "newCipher", "k1");
    verify(users).updatePassword(1L, "newHash");
    verify(ldap, never()).updatePassword(anyString(), anyString());
  }

  @Test
  void editPassword_ldapWriteback() {
    UserAccount u = baseUser();
    u.setIdentity("[\"ldap\"]");
    u.setPassword("hash");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(passwords.requirePlain("k1", "oldCipher")).thenReturn("OldPass1");
    when(passwords.requirePlain("k1", "newCipher")).thenReturn("NewPass1");
    when(passwordEncoder.matches("OldPass1", "hash")).thenReturn(true);
    when(ldap.isEnabled()).thenReturn(true);
    when(ldap.updatePassword("admin@bluedock.local", "NewPass1")).thenReturn(true);
    when(passwordEncoder.encode("NewPass1")).thenReturn("newHash");

    profiles.editPassword("oldCipher", "newCipher", "k1");
    verify(ldap).updatePassword("admin@bluedock.local", "NewPass1");
    verify(users).updatePassword(1L, "newHash");
  }

  @Test
  void editPassword_systemDenied() {
    UserAccount u = baseUser();
    u.setIdentity("[\"system\"]");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> profiles.editPassword("o", "n", "k1"));
    assertEquals(I18nKeys.USER_PASS_SYSTEM_DENIED, ex.getMessageKey());
  }

  @Test
  void editPassword_samePassword() {
    UserAccount u = baseUser();
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(passwords.requirePlain(eq("k1"), anyString())).thenReturn("Same1!");
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> profiles.editPassword("a", "b", "k1"));
    assertEquals(I18nKeys.USER_PASS_SAME, ex.getMessageKey());
  }

  private static UserAccount baseUser() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("admin@bluedock.local");
    u.setNickname("Admin");
    u.setIdentity("[\"admin\"]");
    return u;
  }
}
