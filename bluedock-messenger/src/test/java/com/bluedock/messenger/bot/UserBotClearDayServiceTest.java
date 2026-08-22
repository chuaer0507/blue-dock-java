package com.bluedock.messenger.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.messenger.repo.DialogRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserBotClearDayServiceTest {
  @Mock
  private DialogRepository dialogs;
  @InjectMocks
  private UserBotClearDayService service;

  @Test
  void softDeletesAndAdvancesClearAt() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 6, 12, 0);
    when(dialogs.listUserBotsForClear(any(), anyInt()))
        .thenReturn(List.of(Map.of("id", 1L, "ownerId", 2L, "botId", 9L, "clearDay", 30)));
    when(dialogs.softDeleteBotMessagesBefore(eq(9L), any(), anyInt())).thenReturn(3);

    Map<String, Object> out = service.runAt(now);
    assertEquals(1, out.get("botsProcessed"));
    assertEquals(3, out.get("messagesDeleted"));
    verify(dialogs).softDeleteBotMessagesBefore(eq(9L), eq(now.minusDays(30)), eq(1000));
    verify(dialogs).updateUserBotClearAt(eq(1L), eq(now.plusDays(30)));
  }
}
