package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.oss.OssProperties;
import com.bluedock.common.oss.RuntimeObjectStorage;
import com.bluedock.system.repo.SettingRepository;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class OssSettingServiceTest {
  @Mock SettingRepository settings;
  @Mock RuntimeObjectStorage runtimeObjectStorage;
  @Mock StringRedisTemplate redis;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;

  OssProperties boot = new OssProperties();
  ObjectMapper json = new ObjectMapper();
  OssSettingService service;

  @BeforeEach
  void setUp() {
    service =
        new OssSettingService(
            settings, runtimeObjectStorage, boot, json, redis, adminGuard, writeGuard);
  }

  @Test
  void check_putsThenDeletesProbe() {
    when(runtimeObjectStorage.put(anyString(), any(InputStream.class), anyLong(), eq("text/plain")))
        .thenReturn("https://cdn.example.com/media/oss-check/x.txt");
    when(runtimeObjectStorage.providerId()).thenReturn("local");
    doNothing().when(runtimeObjectStorage).delete(anyString());

    Map<String, Object> out = service.check();

    assertEquals(true, out.get("ok"));
    assertEquals("local", out.get("provider"));
    assertTrue(String.valueOf(out.get("key")).startsWith("media/oss-check/"));
    assertEquals("https://cdn.example.com/media/oss-check/x.txt", out.get("url"));
    verify(adminGuard).requireAdmin();
    verify(runtimeObjectStorage).delete(String.valueOf(out.get("key")));
  }

  @Test
  void check_wrapsUnexpectedFailure() {
    when(runtimeObjectStorage.put(anyString(), any(InputStream.class), anyLong(), eq("text/plain")))
        .thenThrow(new RuntimeException("network"));
    when(runtimeObjectStorage.providerId()).thenReturn("aliyun");

    BusinessException ex = assertThrows(BusinessException.class, () -> service.check());
    assertEquals(I18nKeys.SYSTEM_OSS_CHECK_FAILED, ex.getMessageKey());
  }

  @Test
  void check_rethrowsBusinessException() {
    doThrow(new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SYSTEM_OSS_SAVE_OBJECT_FAILED))
        .when(runtimeObjectStorage)
        .put(anyString(), any(InputStream.class), anyLong(), anyString());

    BusinessException ex = assertThrows(BusinessException.class, () -> service.check());
    assertEquals(I18nKeys.SYSTEM_OSS_SAVE_OBJECT_FAILED, ex.getMessageKey());
  }
}
