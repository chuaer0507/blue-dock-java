package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.export.ExportRunPublisher;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySendPublisher;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceExportServiceTest {
  @Mock AdminGuard adminGuard;
  @Mock AttendanceSettingService attendanceSettings;
  @Mock ExportRunPublisher exportPublisher;
  @Mock ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock StringRedisTemplate redis;

  AttendanceExportService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    doNothing().when(adminGuard).requireAdmin();
    when(notifyPublisher.getIfAvailable()).thenReturn(null);
    when(attendanceSettings.loadPublic()).thenReturn(Map.of("open", "open"));
    when(attendanceSettings.isOpen(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    service =
        new AttendanceExportService(
            adminGuard,
            attendanceSettings,
            exportPublisher,
            notifyPublisher,
            redis,
            new ObjectMapper());
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void export_publishesAttendance() {
    Map<String, Object> out =
        service.export("2,3", "2026-01-01,2026-01-10", "09:00,18:00");
    assertTrue((Boolean) out.get("accepted"));
    ArgumentCaptor<ExportRunEvent> cap = ArgumentCaptor.forClass(ExportRunEvent.class);
    verify(exportPublisher).publish(cap.capture());
    assertEquals(ExportRunEvent.KIND_ATTENDANCE, cap.getValue().kind());
    assertEquals(2, cap.getValue().userIds().size());
    assertEquals("2026-01-01", cap.getValue().timeStart());
    assertEquals("09:00,18:00", cap.getValue().timeType());
  }

  @Test
  void export_requiresOpen() {
    when(attendanceSettings.isOpen(org.mockito.ArgumentMatchers.any())).thenReturn(false);
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.export("2", "2026-01-01,2026-01-02", "09:00,18:00"));
    assertEquals(I18nKeys.ATTENDANCE_DISABLED, ex.getMessageKey());
  }

  @Test
  void export_userLimit() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= 101; i++) {
      if (i > 1) {
        sb.append(',');
      }
      sb.append(i);
    }
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.export(sb.toString(), "2026-01-01,2026-01-02", "09:00,18:00"));
    assertEquals(I18nKeys.EXPORT_USER_LIMIT, ex.getMessageKey());
  }

  @Test
  void export_dateRangeLimit() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.export("2", "2026-01-01,2026-03-01", "09:00,18:00"));
    assertEquals(I18nKeys.EXPORT_TIME_RANGE, ex.getMessageKey());
  }
}
