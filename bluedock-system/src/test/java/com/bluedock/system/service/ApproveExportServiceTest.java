package com.bluedock.system.service;

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
import com.bluedock.common.export.ApproveExportBridge;
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
class ApproveExportServiceTest {
  @Mock AdminGuard adminGuard;
  @Mock ExportRunPublisher exportPublisher;
  @Mock ObjectProvider<ApproveExportBridge> approveBridge;
  @Mock ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock ApproveExportBridge bridge;
  @Mock StringRedisTemplate redis;

  ApproveExportService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    doNothing().when(adminGuard).requireAdmin();
    when(notifyPublisher.getIfAvailable()).thenReturn(null);
    when(approveBridge.getIfAvailable()).thenReturn(bridge);
    when(bridge.available()).thenReturn(true);
    service =
        new ApproveExportService(
            adminGuard,
            exportPublisher,
            approveBridge,
            notifyPublisher,
            redis,
            new ObjectMapper());
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void export_publishesApprove() {
    Map<String, Object> out = service.export("请假", "approved", "2026-01-01,2026-01-31");
    assertTrue((Boolean) out.get("accepted"));
    ArgumentCaptor<ExportRunEvent> cap = ArgumentCaptor.forClass(ExportRunEvent.class);
    verify(exportPublisher).publish(cap.capture());
    assertEquals(ExportRunEvent.KIND_APPROVE, cap.getValue().kind());
    assertEquals("请假", cap.getValue().processName());
    assertEquals("approved", cap.getValue().status());
    assertEquals("2026-01-01", cap.getValue().timeStart());
  }

  @Test
  void export_requiresPlugin() {
    when(approveBridge.getIfAvailable()).thenReturn(null);
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.export("请假", null, "2026-01-01,2026-01-10"));
    assertEquals(I18nKeys.APPROVE_PLUGIN_MISSING, ex.getMessageKey());
  }

  @Test
  void export_requiresProcessName() {
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.export("  ", null, "2026-01-01,2026-01-10"));
    assertEquals(I18nKeys.APPROVE_PROCESS_REQUIRED, ex.getMessageKey());
  }
}
