package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MeetingSettingServiceTest {

  @Test
  void maskSecrets_hidesCertificatesAndKeys() {
    Map<String, Object> raw = MeetingSettingService.defaults();
    raw.put("appId", "app");
    raw.put("appCertificate", "cert");
    raw.put("apiKey", "k");
    raw.put("apiSecret", "s");
    raw.put("channelSalt", "salt");
    Map<String, Object> masked = MeetingSettingService.maskSecrets(raw);
    assertEquals("app", masked.get("appId"));
    assertEquals(MeetingSettingService.SECRET_MASK, masked.get("appCertificate"));
    assertEquals(MeetingSettingService.SECRET_MASK, masked.get("apiKey"));
    assertEquals(MeetingSettingService.SECRET_MASK, masked.get("apiSecret"));
    assertEquals(MeetingSettingService.SECRET_MASK, masked.get("channelSalt"));
  }

  @Test
  void mergeIncoming_keepsSecretsWhenMasked() {
    Map<String, Object> current = MeetingSettingService.defaults();
    current.put("apiSecret", "real");
    Map<String, Object> merged =
        MeetingSettingService.mergeIncoming(
            current, Map.of("apiSecret", MeetingSettingService.SECRET_MASK, "closeIdleMinutes", 15));
    assertEquals("real", merged.get("apiSecret"));
    assertEquals(15, merged.get("closeIdleMinutes"));
  }

  @Test
  void isSecretField() {
    assertTrue(MeetingSettingService.isSecretField("appCertificate"));
    assertTrue(MeetingSettingService.isSecretField("channelSalt"));
    assertFalse(MeetingSettingService.isSecretField("appId"));
  }
}
