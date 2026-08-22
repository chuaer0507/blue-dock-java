package com.bluedock.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.ldap.LdapAuthenticator;
import com.bluedock.auth.ldap.LdapUserInfo;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.common.redis.RedisKeys;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
  @Mock UserAccountRepository users;
  @Mock TokenService tokens;
  @Mock LdapAuthenticator ldap;
  @Mock WirePasswordResolver passwords;
  @Mock CaptchaService captcha;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> valueOps;
  @Mock ObjectProvider<LicenseCapacity> licenseCapacity;
  @Mock ObjectProvider<com.bluedock.common.auth.RegPolicy> regPolicy;

  PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  AuthService authService;
  AtomicInteger failCount = new AtomicInteger(0);

  @BeforeEach
  void wire() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(licenseCapacity.getIfAvailable()).thenReturn(null);
    when(regPolicy.getIfAvailable()).thenReturn(null);
    when(valueOps.get(anyString()))
        .thenAnswer(
            inv -> {
              String key = inv.getArgument(0);
              if (key.startsWith(RedisKeys.loginFail(""))) {
                int n = failCount.get();
                return n == 0 ? null : String.valueOf(n);
              }
              return null;
            });
    when(valueOps.increment(anyString()))
        .thenAnswer(
            inv -> {
              failCount.incrementAndGet();
              return (long) failCount.get();
            });
    authService =
        new AuthService(
            users,
            passwordEncoder,
            tokens,
            passwords,
            captcha,
            redis,
            null,
            null,
            licenseCapacity,
            regPolicy);
  }

  @Test
  void login_ok() {
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("Admin123!");
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("admin@bluedock.local");
    u.setNickname("Admin");
    u.setPassword(passwordEncoder.encode("Admin123!"));
    u.setIdentity("[\"admin\"]");
    when(users.findByEmail("admin@bluedock.local")).thenReturn(Optional.of(u));
    when(tokens.issuePair(1L)).thenReturn(new TokenService.TokenPair("tok", "rtok"));

    LoginResult result =
        authService.login("admin@bluedock.local", "cipher", "keyId-1", "127.0.0.1", null, null, null);
    assertEquals("tok", result.token());
    assertEquals("rtok", result.refreshToken());
    assertEquals(1L, result.user().userId());
    verify(users).touchLogin(eq(1L), eq("127.0.0.1"));
    verify(redis).delete(RedisKeys.loginFail("127.0.0.1"));
  }

  @Test
  void login_badPassword_incrementsFail() {
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("wrong");
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("admin@bluedock.local");
    u.setPassword(passwordEncoder.encode("Admin123!"));
    when(users.findByEmail(anyString())).thenReturn(Optional.of(u));

    assertThrows(
        BusinessException.class,
        () -> authService.login("admin@bluedock.local", "cipher", "keyId-1", "127.0.0.1", null, null, null));
    assertEquals(1, failCount.get());
  }

  @Test
  void login_requiresCaptchaAfterThreshold() {
    failCount.set(3);
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("Admin123!");

    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                authService.login(
                    "admin@bluedock.local", "cipher", "keyId-1", "127.0.0.1", null, null, null));
    assertEquals(ErrorCodes.CAPTCHA_REQUIRED, ex.getCode());
  }

  @Test
  void login_withValidCaptcha_ok() {
    failCount.set(3);
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("Admin123!");
    when(captcha.verifyAndConsume("ck", "Ab12")).thenReturn(true);
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("admin@bluedock.local");
    u.setNickname("Admin");
    u.setPassword(passwordEncoder.encode("Admin123!"));
    when(users.findByEmail("admin@bluedock.local")).thenReturn(Optional.of(u));
    when(tokens.issuePair(1L)).thenReturn(new TokenService.TokenPair("tok", "rtok"));

    LoginResult result =
        authService.login(
            "admin@bluedock.local", "cipher", "keyId-1", "127.0.0.1", null, "ck", "Ab12");
    assertEquals("tok", result.token());
  }

  @Test
  void needCode_reflectsFailCount() {
    assertFalse(authService.needCode("10.0.0.1").need());
    failCount.set(3);
    assertTrue(authService.needCode("10.0.0.1").need());
  }

  @Test
  void login_ldap_creates_user() {
    authService =
        new AuthService(
            users,
            passwordEncoder,
            tokens,
            passwords,
            captcha,
            redis,
            ldap,
            null,
            licenseCapacity,
            regPolicy);
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("secret");
    when(users.findByEmail("alice@corp.com")).thenReturn(Optional.empty());
    when(ldap.isEnabled()).thenReturn(true);
    when(ldap.authenticate("alice@corp.com", "secret"))
        .thenReturn(Optional.of(new LdapUserInfo("alice@corp.com", "Alice", "cn=alice,dc=corp")));
    when(tokens.issuePair(any(Long.class))).thenReturn(new TokenService.TokenPair("tok", "rtok"));

    LoginResult result =
        authService.login("alice@corp.com", "cipher", "keyId-1", "10.0.0.1", null, null, null);
    assertEquals("tok", result.token());
    verify(users).insert(any(UserAccount.class));
  }

  @Test
  void login_ldap_updates_nickname() {
    authService =
        new AuthService(
            users,
            passwordEncoder,
            tokens,
            passwords,
            captcha,
            redis,
            ldap,
            null,
            licenseCapacity,
            regPolicy);
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("secret");
    UserAccount existing = new UserAccount();
    existing.setUserId(9L);
    existing.setEmail("alice@corp.com");
    existing.setNickname("Old");
    existing.setIdentity("[\"ldap\"]");
    existing.setPassword(passwordEncoder.encode("random"));
    when(users.findByEmail("alice@corp.com")).thenReturn(Optional.of(existing));
    when(ldap.isEnabled()).thenReturn(true);
    when(ldap.authenticate("alice@corp.com", "secret"))
        .thenReturn(Optional.of(new LdapUserInfo("alice@corp.com", "Alice", "cn=alice,dc=corp")));
    when(tokens.issuePair(9L)).thenReturn(new TokenService.TokenPair("tok", "rtok"));

    authService.login("alice@corp.com", "cipher", "keyId-1", "10.0.0.1", null, null, null);
    verify(users).updateProfile(any(UserAccount.class));
  }

  @Test
  void login_local_syncs_to_ldap_when_enabled() {
    authService =
        new AuthService(
            users,
            passwordEncoder,
            tokens,
            passwords,
            captcha,
            redis,
            ldap,
            null,
            licenseCapacity,
            regPolicy);
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("Admin123!");
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("admin@bluedock.local");
    u.setNickname("Admin");
    u.setPassword(passwordEncoder.encode("Admin123!"));
    u.setIdentity("[]");
    when(users.findByEmail("admin@bluedock.local")).thenReturn(Optional.of(u));
    when(tokens.issuePair(1L)).thenReturn(new TokenService.TokenPair("tok", "rtok"));
    when(ldap.isEnabled()).thenReturn(true);
    when(ldap.syncLocalUser("admin@bluedock.local", "Admin", "Admin123!")).thenReturn(true);

    authService.login("admin@bluedock.local", "cipher", "keyId-1", "127.0.0.1", null, null, null);
    verify(ldap).syncLocalUser("admin@bluedock.local", "Admin", "Admin123!");
    verify(users).updateIdentity(1L, "[\"ldap\"]");
    verify(ldap, never()).authenticate(anyString(), anyString());
  }

  @Test
  void login_local_skips_ldap() {
    authService =
        new AuthService(
            users,
            passwordEncoder,
            tokens,
            passwords,
            captcha,
            redis,
            ldap,
            null,
            licenseCapacity,
            regPolicy);
    when(passwords.requirePlain("keyId-1", "cipher")).thenReturn("Admin123!");
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setEmail("admin@bluedock.local");
    u.setNickname("Admin");
    u.setPassword(passwordEncoder.encode("Admin123!"));
    u.setIdentity("[\"admin\"]");
    when(users.findByEmail("admin@bluedock.local")).thenReturn(Optional.of(u));
    when(tokens.issuePair(1L)).thenReturn(new TokenService.TokenPair("tok", "rtok"));
    when(ldap.isEnabled()).thenReturn(false);

    authService.login("admin@bluedock.local", "cipher", "keyId-1", "127.0.0.1", null, null, null);
    verify(ldap, never()).authenticate(anyString(), anyString());
    verify(ldap, never()).syncLocalUser(anyString(), anyString(), anyString());
  }

  @Test
  void withLdapIdentity_merge() {
    assertEquals("[\"admin\",\"ldap\"]", AuthService.withLdapIdentity("[\"admin\"]"));
    assertEquals("[\"ldap\"]", AuthService.withLdapIdentity("[\"ldap\"]"));
  }
}
