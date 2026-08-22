package com.bluedock.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrCodeLoginServiceTest {
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock AuthService auth;

  ObjectMapper objectMapper = new ObjectMapper();
  QrCodeLoginService service;
  String stored;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString()))
        .thenAnswer(inv -> stored);
    when(redis.getExpire(anyString())).thenReturn(20L);
    org.mockito.Mockito.doAnswer(
            inv -> {
              stored = inv.getArgument(1);
              return null;
            })
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    service = new QrCodeLoginService(redis, objectMapper, auth);
  }

  @AfterEach
  void tearDown() {
    AuthContext.clear();
    stored = null;
  }

  @Test
  void create_returnsCodeAtLeast32() {
    Map<String, Object> out = service.handle("create", null, "1.1.1.1", "ua");
    String code = String.valueOf(out.get("code"));
    assertTrue(code.length() >= 32);
    assertEquals("waiting", out.get("status"));
    assertEquals(30L, out.get("expire"));
    verify(values).set(eq(RedisKeys.qrCode(code)), anyString(), eq(QrCodeLoginService.TTL));
  }

  @Test
  void confirm_requiresAuth() {
    Map<String, Object> created = service.handle("create", null, "ip", "ua");
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.handle("confirm", String.valueOf(created.get("code")), "ip", "ua"));
    assertEquals(I18nKeys.AUTH_QR_CODE_AUTH_REQUIRED, ex.getMessageKey());
  }

  @Test
  void status_issuesTokenOnceConfirmed() {
    Map<String, Object> created = service.handle("create", null, "ip", "ua");
    String code = String.valueOf(created.get("code"));

    AuthContext.set(new AuthUser(9L));
    doNothing().when(auth).assertCanLogin(9L);
    service.handle("confirm", code, "ip", "ua");
    AuthContext.clear();

    UserPublicView user =
        new UserPublicView(9L, "a@b.com", "A", "", "[]", "", "", "", "", "", "");
    when(auth.loginByUserId(eq(9L), eq("1.2.3.4"), eq("Desktop")))
        .thenReturn(new LoginResult("tok", "rtok", user));

    Map<String, Object> ok = service.handle("status", code, "1.2.3.4", "Desktop");
    assertEquals("success", ok.get("status"));
    assertEquals("tok", ok.get("token"));
    verify(auth).loginByUserId(9L, "1.2.3.4", "Desktop");

    BusinessException used =
        assertThrows(BusinessException.class, () -> service.handle("status", code, "ip", "ua"));
    assertEquals(I18nKeys.AUTH_QR_CODE_USED, used.getMessageKey());
  }

  @Test
  void status_shortCodeRejected() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.handle("status", "short", "ip", "ua"));
    assertEquals(I18nKeys.AUTH_QR_CODE_INVALID, ex.getMessageKey());
  }

  @Test
  void status_expiredWhenMissing() {
    String code = "a".repeat(32);
    stored = null;
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.handle("status", code, "ip", "ua"));
    assertEquals(I18nKeys.AUTH_QR_CODE_EXPIRED, ex.getMessageKey());
  }
}
