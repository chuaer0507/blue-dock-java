package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppPushSettingServiceTest {

  @Test
  void maskSecrets_hidesKeys() {
    Map<String, Object> raw = AppPushSettingService.defaults();
    raw.put("iosKey", "ik");
    raw.put("iosSecret", "is");
    raw.put("androidKey", "ak");
    raw.put("androidSecret", "as");
    Map<String, Object> masked = AppPushSettingService.maskSecrets(raw);
    assertEquals(AppPushSettingService.SECRET_MASK, masked.get("iosKey"));
    assertEquals(AppPushSettingService.SECRET_MASK, masked.get("iosSecret"));
    assertEquals(AppPushSettingService.SECRET_MASK, masked.get("androidKey"));
    assertEquals(AppPushSettingService.SECRET_MASK, masked.get("androidSecret"));
    assertEquals("bluedock", masked.get("aliasType"));
  }

  @Test
  void mergeIncoming_keepsSecretsWhenBlank() {
    Map<String, Object> current = AppPushSettingService.defaults();
    current.put("androidSecret", "real");
    Map<String, Object> merged =
        AppPushSettingService.mergeIncoming(current, Map.of("androidSecret", "", "open", "open"));
    assertEquals("real", merged.get("androidSecret"));
    assertEquals("open", merged.get("open"));
  }

  @Test
  void isSecretField() {
    assertTrue(AppPushSettingService.isSecretField("iosKey"));
    assertFalse(AppPushSettingService.isSecretField("aliasType"));
  }
}
