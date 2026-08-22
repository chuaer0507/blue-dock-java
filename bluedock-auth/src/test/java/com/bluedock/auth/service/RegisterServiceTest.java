package com.bluedock.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.auth.AuthMailBridge;
import com.bluedock.common.auth.RegPolicy;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.redis.RedisKeys;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterServiceTest {
  @Mock UserAccountRepository users;
  @Mock WirePasswordResolver passwords;
  @Mock PasswordEncoder passwordEncoder;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock AuthService authService;
  @Mock ObjectProvider<RegPolicy> regPolicyProvider;
  @Mock ObjectProvider<com.bluedock.common.license.LicenseCapacity> licenseCapacity;
  @Mock RegPolicy regPolicy;
  @Mock AuthMailBridge mail;

  RegisterService service;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    when(regPolicyProvider.getIfAvailable()).thenReturn(regPolicy);
    when(licenseCapacity.getIfAvailable()).thenReturn(null);
    when(regPolicy.isRegistrationClosed()).thenReturn(false);
    when(regPolicy.isRegVerifyOpen()).thenReturn(false);
    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    service =
        new RegisterService(
            users,
            passwords,
            passwordEncoder,
            redis,
            authService,
            regPolicyProvider,
            licenseCapacity,
            mail);
  }

  @Test
  void sendEmailCode_reg_returnsDevCodeWhenMailUnconfigured() {
    when(users.existsByEmail("a@b.com")).thenReturn(false);
    when(redis.hasKey(anyString())).thenReturn(false);
    when(mail.send(anyString(), anyString(), anyString())).thenReturn(false);

    Map<String, Object> out = service.sendEmailCode("a@b.com", "reg");
    assertTrue(out.containsKey("devCode"));
    verify(values)
        .set(
            eq(RedisKeys.authEmailCode("reg", "a@b.com")),
            anyString(),
            any(java.time.Duration.class));
  }

  @Test
  void sendEmailCode_closed_throws() {
    when(regPolicy.isRegistrationClosed()).thenReturn(true);
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.sendEmailCode("a@b.com", "reg"));
    assertEquals(ErrorCodes.BAD_REQUEST, ex.getCode());
  }

  @Test
  void register_ok_issuesToken() {
    when(users.existsByEmail("a@b.com")).thenReturn(false);
    when(values.get(RedisKeys.authEmailCode("reg", "a@b.com"))).thenReturn("123456");
    when(passwords.requirePlain("k1", "cipher")).thenReturn("Secret1");
    when(authService.loginByUserId(anyLong(), anyString(), any()))
        .thenReturn(
            new LoginResult(
                "tok",
                "rtok",
                new UserPublicView(
                    1L, "a@b.com", "nick", "", "", "", "", "", "", "", "")));

    Map<String, Object> out =
        service.register("a@b.com", "cipher", "k1", "123456", "nick", null, "127.0.0.1", "ua");
    assertEquals("tok", out.get("token"));
    assertEquals("rtok", out.get("refreshToken"));
    ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).insert(cap.capture());
    assertEquals(1, cap.getValue().getEmailVerify());
    assertEquals(0, cap.getValue().getMustChangePassword());
  }

  @Test
  void resetPassword_ok() {
    UserAccount u = new UserAccount();
    u.setUserId(9L);
    u.setEmail("a@b.com");
    when(values.get(RedisKeys.authEmailCode("reset", "a@b.com"))).thenReturn("654321");
    when(users.findByEmail("a@b.com")).thenReturn(Optional.of(u));
    when(passwords.requirePlain("k1", "cipher")).thenReturn("Secret1");

    Map<String, Object> out = service.resetPassword("a@b.com", "654321", "cipher", "k1");
    assertEquals(true, out.get("ok"));
    verify(users).updatePassword(eq(9L), eq("hash"));
  }

  @Test
  void resetPassword_badCode() {
    when(values.get(RedisKeys.authEmailCode("reset", "a@b.com"))).thenReturn("000000");
    assertThrows(
        BusinessException.class,
        () -> service.resetPassword("a@b.com", "111111", "cipher", "k1"));
    verify(users, never()).updatePassword(anyLong(), anyString());
  }
}
