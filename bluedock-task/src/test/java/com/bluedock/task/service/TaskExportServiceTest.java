package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.export.ExportRunPublisher;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.system.service.AdminGuard;
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
class TaskExportServiceTest {
  @Mock
  AdminGuard adminGuard;
  @Mock
  ExportRunPublisher exportPublisher;
  @Mock
  ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock
  StringRedisTemplate redis;

  TaskExportService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    doNothing().when(adminGuard).requireAdmin();
    when(notifyPublisher.getIfAvailable()).thenReturn(null);
    service = new TaskExportService(
        adminGuard, exportPublisher, notifyPublisher, redis, new ObjectMapper());
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void exportStats_publishes() {
    Map<String, Object> out = service.exportStats("2,3", "2026-01-01,2026-01-10", "taskTime");
    assertTrue((Boolean) out.get("accepted"));
    ArgumentCaptor<ExportRunEvent> cap = ArgumentCaptor.forClass(ExportRunEvent.class);
    verify(exportPublisher).publish(cap.capture());
    assertEquals(ExportRunEvent.KIND_TASK_STATS, cap.getValue().kind());
    assertEquals(2, cap.getValue().userIds().size());
    assertEquals("2026-01-01", cap.getValue().timeStart());
  }

  @Test
  void exportStats_userLimit() {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= 101; i++) {
      if (i > 1) {
        sb.append(',');
      }
      sb.append(i);
    }
    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.exportStats(sb.toString(), "2026-01-01,2026-01-10", null));
    assertEquals(I18nKeys.EXPORT_USER_LIMIT, ex.getMessageKey());
  }

  @Test
  void exportOverdue_publishes() {
    service.exportOverdue();
    ArgumentCaptor<ExportRunEvent> cap = ArgumentCaptor.forClass(ExportRunEvent.class);
    verify(exportPublisher).publish(cap.capture());
    assertEquals(ExportRunEvent.KIND_TASK_OVERDUE, cap.getValue().kind());
  }
}
