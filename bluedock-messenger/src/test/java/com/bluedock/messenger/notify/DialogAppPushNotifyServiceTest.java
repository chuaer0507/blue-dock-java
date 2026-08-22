package com.bluedock.messenger.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.web.dto.DialogMessageView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class DialogAppPushNotifyServiceTest {
  @Mock DialogRepository dialogs;
  @Mock UserAccountRepository users;
  @Mock ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock NotifySendPublisher publisher;

  DialogAppPushNotifyService service;

  @BeforeEach
  void setUp() {
    service = new DialogAppPushNotifyService(dialogs, users, notifyPublisher);
  }

  @Test
  void previewAndWeakType() {
    assertEquals("[图片]", DialogAppPushNotifyService.previewOf("image", "x"));
    assertEquals("hello", DialogAppPushNotifyService.previewOf("text", "hello"));
    assertTrue(DialogAppPushNotifyService.isWeakType("notice"));
    assertFalse(DialogAppPushNotifyService.isEventSilent(Map.of()));
    assertTrue(DialogAppPushNotifyService.isEventSilent(Map.of("silence", "yes")));
  }

  @Test
  void afterFanout_skipsMutedExceptMention() {
    when(notifyPublisher.getIfAvailable()).thenReturn(publisher);
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setName("工程群");
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of(2L, true, 3L, false));
    when(dialogs.sumUnreadForUser(2L)).thenReturn(3);
    when(dialogs.sumUnreadForUser(3L)).thenReturn(1);

    UserAccount u2 = new UserAccount();
    u2.setUserId(2L);
    u2.setIsBot(0);
    UserAccount u3 = new UserAccount();
    u3.setUserId(3L);
    u3.setIsBot(0);
    when(users.findByUserId(2L)).thenReturn(Optional.of(u2));
    when(users.findByUserId(3L)).thenReturn(Optional.of(u3));
    when(users.findByUserId(1L)).thenReturn(Optional.of(sender()));

    DialogMessageView msg =
        new DialogMessageView(100L, 9L, 1L, "text", "hi @2", 0L, 0L, LocalDateTime.now());
    service.afterDialogMessageFanout(
        RealtimeEventTypes.DIALOG_MESSAGE,
        List.of(1L, 2L, 3L),
        Map.of("dialogId", 9L, "message", msg, "mentionUserIds", List.of(2L)));

    ArgumentCaptor<NotifySendEvent> cap = ArgumentCaptor.forClass(NotifySendEvent.class);
    verify(publisher, org.mockito.Mockito.times(2)).publish(cap.capture());
    assertEquals(
        List.of(2L, 3L),
        cap.getAllValues().stream().flatMap(e -> e.userIds().stream()).toList());
    assertEquals(true, cap.getAllValues().get(0).data().get("mentioned"));
  }

  @Test
  void afterFanout_skipsWhenSilent() {
    DialogMessageView msg =
        new DialogMessageView(100L, 9L, 1L, "text", "x", 0L, 0L, LocalDateTime.now());
    service.afterDialogMessageFanout(
        RealtimeEventTypes.DIALOG_MESSAGE,
        List.of(1L, 2L),
        Map.of("message", msg, "isSilent", true));
    verify(notifyPublisher, never()).getIfAvailable();
  }

  private static UserAccount sender() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setNickname("Alice");
    u.setIsBot(0);
    return u;
  }
}
