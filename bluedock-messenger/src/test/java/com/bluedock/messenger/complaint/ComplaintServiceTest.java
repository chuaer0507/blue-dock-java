package com.bluedock.messenger.complaint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.system.service.AdminGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ComplaintServiceTest {
  @Mock ComplaintRepository complaints;
  @Mock DialogRepository dialogs;
  @Mock AdminGuard adminGuard;
  @Mock ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock NotifySendPublisher publisher;

  ComplaintService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(5L));
    service = new ComplaintService(complaints, dialogs, adminGuard, notifyPublisher);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void submit_ok_andNotifies() {
    Dialog d = new Dialog();
    d.setId(100L);
    when(dialogs.findActive(100L)).thenReturn(Optional.of(d));
    when(dialogs.isMember(100L, 5L)).thenReturn(true);
    when(notifyPublisher.getIfAvailable()).thenReturn(publisher);
    when(complaints.listRecentAdminIds(10)).thenReturn(List.of(1L, 2L));

    Map<String, Object> out =
        service.submit(100L, 10, "spam ads", List.of(Map.of("path", "media/a.png")));
    assertTrue((Boolean) out.get("ok"));
    verify(complaints).insert(any(Complaint.class));
    ArgumentCaptor<NotifySendEvent> cap = ArgumentCaptor.forClass(NotifySendEvent.class);
    verify(publisher).publish(cap.capture());
    assertEquals(NotifySendEvent.CHANNEL_DESKTOP, cap.getValue().channel());
    assertEquals(List.of(1L, 2L), cap.getValue().userIds());
  }

  @Test
  void submit_badType() {
    Dialog d = new Dialog();
    d.setId(100L);
    when(dialogs.findActive(100L)).thenReturn(Optional.of(d));
    when(dialogs.isMember(100L, 5L)).thenReturn(true);
    assertThrows(BusinessException.class, () -> service.submit(100L, 99, "x", List.of()));
    verify(complaints, never()).insert(any());
  }

  @Test
  void lists_requiresAdmin() {
    when(complaints.count(null, null)).thenReturn(0L);
    when(complaints.page(null, null, 0, 50)).thenReturn(List.of());
    Map<String, Object> out = service.lists(null, null, 1, 50);
    verify(adminGuard).requireAdmin();
    assertEquals(0L, out.get("total"));
  }

  @Test
  void action_handle() {
    Complaint row = new Complaint();
    row.setId(9L);
    when(complaints.findById(9L)).thenReturn(Optional.of(row));
    Map<String, Object> out = service.action(9L, "handle");
    verify(adminGuard).requireAdmin();
    verify(complaints).updateStatus(9L, 1);
    assertEquals(1, out.get("status"));
  }

  @Test
  void action_delete() {
    Complaint row = new Complaint();
    row.setId(9L);
    when(complaints.findById(9L)).thenReturn(Optional.of(row));
    service.action(9L, "delete");
    verify(complaints).deleteById(9L);
  }
}
