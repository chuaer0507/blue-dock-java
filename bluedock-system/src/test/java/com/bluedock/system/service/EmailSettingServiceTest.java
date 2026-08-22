package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailSettingServiceTest {

  @Test
  void maskSecrets_hidesPasswordWhenSet() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("smtpHost", "smtp.example.com");
    raw.put("smtpPassword", "secret");
    Map<String, Object> masked = EmailSettingService.maskSecrets(raw);
    assertEquals(EmailSettingService.SECRET_MASK, masked.get("smtpPassword"));
    assertEquals("smtp.example.com", masked.get("smtpHost"));
  }

  @Test
  void mergeIncoming_keepsPasswordWhenBlankOrMask() {
    Map<String, Object> current = EmailSettingService.defaults();
    current.put("smtpHost", "smtp.example.com");
    current.put("smtpPassword", "real-secret");

    Map<String, Object> blank = EmailSettingService.mergeIncoming(current, Map.of("smtpPassword", ""));
    assertEquals("real-secret", blank.get("smtpPassword"));

    Map<String, Object> mask =
        EmailSettingService.mergeIncoming(
            current, Map.of("smtpPassword", EmailSettingService.SECRET_MASK, "smtpPort", "587"));
    assertEquals("real-secret", mask.get("smtpPassword"));
    assertEquals("587", mask.get("smtpPort"));
  }

  @Test
  void mergeIncoming_updatesPasswordWhenProvided() {
    Map<String, Object> current = EmailSettingService.defaults();
    current.put("smtpPassword", "old");
    Map<String, Object> merged =
        EmailSettingService.mergeIncoming(current, Map.of("smtpPassword", "new-pass"));
    assertEquals("new-pass", merged.get("smtpPassword"));
    assertTrue(merged.containsKey("smtpHost"));
  }
}
