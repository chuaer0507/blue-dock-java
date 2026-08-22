package com.bluedock.messenger.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.export.ExportNotifyBridge;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.service.DialogService;
import com.bluedock.messenger.web.dto.DialogMessageView;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessengerExportNotifyBridgeTest {
  @Mock UserAccountRepository users;
  @Mock DialogRepository dialogs;
  @Mock DialogService dialogService;

  @Test
  void sendDm_missingBot_returnsZero() {
    when(users.findByEmail(ExportNotifyBridge.BOT_EMAIL)).thenReturn(Optional.empty());
    MessengerExportNotifyBridge bridge =
        new MessengerExportNotifyBridge(users, dialogs, dialogService);
    assertEquals(0L, bridge.sendDm(7L, "hello"));
    verify(dialogService, never()).sendTextAsBot(anyLong(), anyLong(), anyString());
  }

  @Test
  void sendDm_ok() {
    UserAccount bot = new UserAccount();
    bot.setUserId(2L);
    bot.setIsBot(1);
    when(users.findByEmail(ExportNotifyBridge.BOT_EMAIL)).thenReturn(Optional.of(bot));
    when(users.existsByUserId(7L)).thenReturn(true);
    when(dialogs.findUserDialogId(2L, 7L)).thenReturn(Optional.of(99L));
    DialogMessageView view = mock(DialogMessageView.class);
    when(view.id()).thenReturn(55L);
    when(dialogService.sendTextAsBot(eq(2L), eq(99L), eq("导出完成"))).thenReturn(view);

    MessengerExportNotifyBridge bridge =
        new MessengerExportNotifyBridge(users, dialogs, dialogService);
    assertEquals(55L, bridge.sendDm(7L, "导出完成"));
  }
}
