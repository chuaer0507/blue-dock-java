package com.bluedock.messenger.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.todo.TodoAlertRemindBridge;
import com.bluedock.messenger.repo.DialogRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DialogTodoRemindServiceTest {
  @Mock private DialogRepository dialogs;
  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> values;
  @Mock private ObjectProvider<TodoAlertRemindBridge> bridgeProvider;
  @Mock private TodoAlertRemindBridge bridge;
  @InjectMocks private DialogTodoRemindService service;

  @Test
  void sendsDmOncePerTodo() {
    when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(bridge.sendDm(eq(7L), anyString())).thenReturn(99L);
    LocalDateTime now = LocalDateTime.of(2026, 8, 6, 12, 0);
    when(dialogs.listDueTodos(any(), anyInt()))
        .thenReturn(List.of(Map.of("id", 3L, "userId", 7L, "messageId", 1L, "dialogId", 2L)));

    Map<String, Object> out = service.runAt(now);
    assertEquals(1, out.get("sent"));
    verify(values).setIfAbsent(eq(RedisKeys.dialogTodoRemindSent(3L)), eq("1"), any(Duration.class));
    verify(bridge).sendDm(eq(7L), anyString());
  }
}
