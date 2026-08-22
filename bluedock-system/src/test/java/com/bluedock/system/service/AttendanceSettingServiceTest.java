package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AttendanceSettingServiceTest {
  @Test
  void maskSecrets_hidesMapKeyAndReportKey() {
    Map<String, Object> raw = AttendanceSettingService.defaults();
    raw.put("mapKey", "secret-map");
    raw.put("reportKey", "secret-report");
    Map<String, Object> masked = AttendanceSettingService.maskSecrets(raw);
    assertEquals(AttendanceSettingService.SECRET_MASK, masked.get("mapKey"));
    assertEquals(AttendanceSettingService.SECRET_MASK, masked.get("reportKey"));
  }

  @Test
  void mergeIncoming_keepsSecretsWhenMasked() {
    Map<String, Object> current = AttendanceSettingService.defaults();
    current.put("mapKey", "real");
    Map<String, Object> merged =
        AttendanceSettingService.mergeIncoming(
            current, Map.of("mapKey", AttendanceSettingService.SECRET_MASK, "locationRadius", 200));
    assertEquals("real", merged.get("mapKey"));
    assertEquals(200, merged.get("locationRadius"));
  }

  @Test
  void locationRadius_clamped() {
    AttendanceSettingService svc =
        new AttendanceSettingService(null, null, null, null);
    assertEquals(50, svc.locationRadius(Map.of("locationRadius", 10)));
    assertEquals(5000, svc.locationRadius(Map.of("locationRadius", 99999)));
    assertEquals(500, svc.locationRadius(Map.of()));
  }

  @Test
  void locationConfigured() {
    AttendanceSettingService svc =
        new AttendanceSettingService(null, null, null, null);
    assertFalse(svc.locationConfigured(Map.of("locationLatitude", 0, "locationLongitude", 0)));
    assertTrue(svc.locationConfigured(Map.of("locationLatitude", 31.2, "locationLongitude", 0)));
  }
}
