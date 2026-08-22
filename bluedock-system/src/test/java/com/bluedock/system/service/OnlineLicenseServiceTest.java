package com.bluedock.system.service;

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

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.config.SystemProperties;
import com.bluedock.system.license.LicenseOnlineClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OnlineLicenseServiceTest {
  @Mock LicenseService licenses;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock LicenseOnlineClient onlineClient;

  SystemProperties props;
  OnlineLicenseService service;

  @BeforeEach
  void setUp() {
    props = new SystemProperties();
    props.setLicenseOnlineMode("local");
    props.setMachineSn("SN-TEST");
    props.setLicenseTrialDays(14);
    props.setLicenseTrialPeople(3);
    when(redis.opsForValue()).thenReturn(values);
    when(licenses.applyOnline(any())).thenReturn(Map.of("ok", true, "online", true));
    service = new OnlineLicenseService(licenses, props, redis, onlineClient);
  }

  @Test
  void sendEmail_returnsDevCodeInLocal() {
    Map<String, Object> sent = service.sendEmail("a@b.com");
    assertTrue((Boolean) sent.get("sent"));
    assertTrue(sent.containsKey("devCode"));
    verify(values).set(eq(RedisKeys.licenseOnlineCode("a@b.com")), anyString(), any(Duration.class));
    verify(onlineClient, never()).post(anyString(), anyString(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void login_confirm_writesOnlineLicense() {
    when(values.get(RedisKeys.licenseOnlineCode("a@b.com"))).thenReturn("123456");
    Map<String, Object> login = service.login("a@b.com", "123456");
    String token = String.valueOf(login.get("token"));
    when(values.get(RedisKeys.licenseOnlinePending(token))).thenReturn("a@b.com");

    service.confirm(token);
    ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
    verify(licenses).applyOnline(cap.capture());
    assertEquals(true, cap.getValue().get("online"));
    assertEquals("a@b.com", cap.getValue().get("onlineEmail"));
    assertEquals(10, cap.getValue().get("people"));
  }

  @Test
  void trial_once() {
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    service.trial("t@x.com");
    verify(licenses).applyOnline(any());
  }

  @Test
  void trial_used() {
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);
    BusinessException ex = assertThrows(BusinessException.class, () -> service.trial(null));
    assertEquals(I18nKeys.LICENSE_TRIAL_USED, ex.getMessageKey());
  }

  @Test
  void remoteWithoutUrl_unavailable() {
    props.setLicenseOnlineMode("remote");
    props.setLicenseOnlineUrl("");
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.sendEmail("a@b.com"));
    assertEquals(I18nKeys.LICENSE_ONLINE_UNAVAILABLE, ex.getMessageKey());
  }

  @Test
  @SuppressWarnings("unchecked")
  void remote_sendEmail_andConfirm() {
    props.setLicenseOnlineMode("remote");
    props.setLicenseOnlineUrl("https://store.example.com/");
    when(onlineClient.post(eq("https://store.example.com/"), eq("/v1/license/email/send"), any()))
        .thenReturn(Map.of("sent", true, "expiresIn", 600));
    when(onlineClient.post(eq("https://store.example.com/"), eq("/v1/license/login"), any()))
        .thenReturn(Map.of("token", "tok-1", "email", "a@b.com"));
    when(onlineClient.post(eq("https://store.example.com/"), eq("/v1/license/confirm"), any()))
        .thenReturn(
            Map.of(
                "people",
                20,
                "sn",
                "SN-TEST",
                "macAddresses",
                List.of(),
                "expiredAt",
                "2099-01-01",
                "onlineEmail",
                "a@b.com",
                "license",
                "remote:a@b.com"));

    Map<String, Object> sent = service.sendEmail("a@b.com");
    assertTrue((Boolean) sent.get("sent"));
    assertFalse(sent.containsKey("devCode"));

    Map<String, Object> login = service.login("a@b.com", "999999");
    assertEquals("tok-1", login.get("token"));

    service.confirm("tok-1");
    ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
    verify(licenses).applyOnline(cap.capture());
    assertEquals(20, cap.getValue().get("people"));
    assertEquals(true, cap.getValue().get("online"));
    assertEquals("a@b.com", cap.getValue().get("onlineEmail"));
    verify(values, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void remote_trial_mapsError() {
    props.setLicenseOnlineMode("remote");
    props.setLicenseOnlineUrl("https://store.example.com");
    when(onlineClient.post(anyString(), eq("/v1/license/trial"), any()))
        .thenThrow(new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.LICENSE_TRIAL_USED));
    BusinessException ex = assertThrows(BusinessException.class, () -> service.trial("a@b.com"));
    assertEquals(I18nKeys.LICENSE_TRIAL_USED, ex.getMessageKey());
  }

  @Test
  void toLicensePayload_nestedDataAndMacsAlias() {
    Map<String, Object> remote =
        Map.of(
            "data",
            Map.of(
                "people",
                5,
                "sn",
                "S1",
                "macs",
                List.of("AA:BB"),
                "expired_at",
                "2099-12-31",
                "email",
                "x@y.com"));
    Map<String, Object> payload = OnlineLicenseService.toLicensePayload(remote, true);
    assertEquals(5, payload.get("people"));
    assertEquals("S1", payload.get("sn"));
    assertEquals(List.of("AA:BB"), payload.get("macAddresses"));
    assertEquals("2099-12-31", payload.get("expiredAt"));
    assertEquals("x@y.com", payload.get("onlineEmail"));
    assertEquals(true, payload.get("online"));
  }
}
