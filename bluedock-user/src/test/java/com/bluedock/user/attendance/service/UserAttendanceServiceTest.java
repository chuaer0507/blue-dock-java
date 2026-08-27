package com.bluedock.user.attendance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.attendance.AttendanceFaceBridge;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.system.service.AttendanceSettingService;
import com.bluedock.user.attendance.repo.UserAttendanceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAttendanceServiceTest {
  @Mock UserAttendanceRepository attendances;
  @Mock AttendanceSettingService settings;
  @Mock ObjectProvider<AttendanceFaceBridge> faceProvider;
  @Mock AttendanceFaceBridge face;
  ObjectMapper json = new ObjectMapper();
  UserAttendanceService service;

  @BeforeEach
  void setUp() {
    when(faceProvider.getIfAvailable()).thenReturn(null);
    service = new UserAttendanceService(attendances, settings, json, faceProvider);
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void get_ok() {
    Map<String, Object> cfg = openCfg();
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(true);
    when(settings.modes(cfg)).thenReturn(List.of("manual", "auto"));
    when(settings.workTime(cfg)).thenReturn(new String[] {"09:00", "18:00"});
    when(settings.editAllowed(cfg)).thenReturn(true);
    when(settings.faceUploadAllowed(cfg)).thenReturn(true);
    when(attendances.listMacAddresses(1L)).thenReturn(List.of("AA:BB:CC:DD:EE:FF"));
    when(attendances.findRecord(eq(1L), any())).thenReturn(Optional.empty());
    when(attendances.hasFace(1L)).thenReturn(false);

    Map<String, Object> view = service.get();
    assertEquals("open", view.get("open"));
    assertEquals(1, ((List<?>) view.get("macAddresses")).size());
    assertEquals(false, view.get("hasFace"));
    assertEquals(false, view.get("facePlugin"));
  }

  @Test
  void save_punch_whenClosed() {
    Map<String, Object> cfg = openCfg();
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(false);
    assertThrows(
        BusinessException.class, () -> service.save(null, 1, null, null, null, null));
  }

  @Test
  void report_ok() {
    Map<String, Object> cfg = openCfg();
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(true);
    when(settings.modeEnabled(cfg, "auto")).thenReturn(true);
    when(settings.reportKey(cfg)).thenReturn("secret");
    when(settings.workTime(cfg)).thenReturn(new String[] {"00:00", "23:59"});
    when(settings.advance(cfg)).thenReturn(0);
    when(settings.delay(cfg)).thenReturn(0);
    when(attendances.findUserIdByMacAddress("AA:BB:CC:DD:EE:FF")).thenReturn(Optional.of(9L));
    when(attendances.findRecord(eq(9L), any())).thenReturn(Optional.empty());

    Map<String, Object> out = service.report("aa:bb:cc:dd:ee:ff", "secret");
    assertEquals(9L, out.get("userId"));
    verify(attendances).upsertRecord(eq(9L), any(), any());
  }

  @Test
  void locationPunch_withinRadius() {
    Map<String, Object> cfg = openCfg();
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(true);
    when(settings.modeEnabled(cfg, "locat")).thenReturn(true);
    when(settings.locationConfigured(cfg)).thenReturn(true);
    when(settings.locationLatitude(cfg)).thenReturn(31.2304);
    when(settings.locationLongitude(cfg)).thenReturn(121.4737);
    when(settings.locationRadius(cfg)).thenReturn(500);
    when(settings.workTime(cfg)).thenReturn(new String[] {"00:00", "23:59"});
    when(settings.advance(cfg)).thenReturn(0);
    when(settings.delay(cfg)).thenReturn(0);
    when(settings.modes(cfg)).thenReturn(List.of("locat"));
    when(settings.editAllowed(cfg)).thenReturn(true);
    when(settings.faceUploadAllowed(cfg)).thenReturn(true);
    when(attendances.listMacAddresses(1L)).thenReturn(List.of());
    when(attendances.findRecord(eq(1L), any())).thenReturn(Optional.empty());
    when(attendances.hasFace(1L)).thenReturn(false);

    Map<String, Object> out = service.save(null, null, 31.2305, 121.4738, null, null);
    assertEquals("open", out.get("open"));
    verify(attendances).upsertRecord(eq(1L), any(), any());
  }

  @Test
  void locationPunch_outsideRadius() {
    Map<String, Object> cfg = openCfg();
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(true);
    when(settings.modeEnabled(cfg, "locat")).thenReturn(true);
    when(settings.locationConfigured(cfg)).thenReturn(true);
    when(settings.locationLatitude(cfg)).thenReturn(31.2304);
    when(settings.locationLongitude(cfg)).thenReturn(121.4737);
    when(settings.locationRadius(cfg)).thenReturn(50);

    assertThrows(
        BusinessException.class, () -> service.save(null, null, 31.24, 121.48, null, null));
  }

  @Test
  void faceEnroll_requiresPlugin() {
    Map<String, Object> cfg = openCfg();
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(true);

    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.save(null, null, null, null, 88L, null));
    assertEquals(I18nKeys.ATTENDANCE_FACE_PLUGIN_MISSING, ex.getMessageKey());
    verify(attendances, never()).upsertFace(anyLong(), anyLong());
  }

  @Test
  void facePunch_ok() {
    Map<String, Object> cfg = openCfg();
    when(faceProvider.getIfAvailable()).thenReturn(face);
    when(face.available()).thenReturn(true);
    when(face.match(1L, 10L, 20L)).thenReturn(true);
    when(settings.loadPublic()).thenReturn(cfg);
    when(settings.isOpen(cfg)).thenReturn(true);
    when(settings.modeEnabled(cfg, "face")).thenReturn(true);
    when(settings.workTime(cfg)).thenReturn(new String[] {"00:00", "23:59"});
    when(settings.advance(cfg)).thenReturn(0);
    when(settings.delay(cfg)).thenReturn(0);
    when(settings.modes(cfg)).thenReturn(List.of("face"));
    when(settings.editAllowed(cfg)).thenReturn(true);
    when(settings.faceUploadAllowed(cfg)).thenReturn(true);
    when(attendances.findFaceUploadObjectId(1L)).thenReturn(Optional.of(10L));
    when(attendances.uploadObjectExists(20L)).thenReturn(true);
    when(attendances.listMacAddresses(1L)).thenReturn(List.of());
    when(attendances.findRecord(eq(1L), any())).thenReturn(Optional.empty());
    when(attendances.hasFace(1L)).thenReturn(true);

    Map<String, Object> out = service.save(null, null, null, null, null, 20L);
    assertTrue((Boolean) out.get("hasFace"));
    verify(attendances).upsertRecord(eq(1L), any(), any());
  }

  @Test
  void distanceMeters_samePointIsZero() {
    assertEquals(0.0, UserAttendanceService.distanceMeters(31.2, 121.4, 31.2, 121.4), 0.01);
  }

  private static Map<String, Object> openCfg() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("open", "open");
    return m;
  }
}
