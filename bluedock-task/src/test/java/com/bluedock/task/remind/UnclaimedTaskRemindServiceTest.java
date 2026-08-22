package com.bluedock.task.remind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.project.UnclaimedTaskRemindBridge;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.repo.TaskRepository;
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
class UnclaimedTaskRemindServiceTest {
  @Mock private SystemGeneralSettingService settings;
  @Mock private TaskRepository tasks;
  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> values;
  @Mock private ObjectProvider<UnclaimedTaskRemindBridge> bridgeProvider;
  @Mock private UnclaimedTaskRemindBridge bridge;
  @InjectMocks private UnclaimedTaskRemindService service;

  @Test
  void sendsInWindow() {
    when(settings.isUnclaimedTaskReminderOpen()).thenReturn(true);
    when(settings.unclaimedTaskReminderTime()).thenReturn("09:00");
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
    when(bridge.sendToDialog(eq(88L), anyString())).thenReturn(1L);
    when(tasks.listUnclaimedTasks(anyInt()))
        .thenReturn(
            List.of(Map.of("id", 1L, "name", "待办A", "projectId", 3L, "dialogId", 88L)));

    Map<String, Object> out = service.runAt(LocalDateTime.of(2026, 8, 6, 9, 0));
    assertEquals(1, out.get("sent"));
    verify(values)
        .setIfAbsent(eq(RedisKeys.unclaimedTaskRemindSent("2026-08-06")), eq("1"), any(Duration.class));
    verify(bridge).sendToDialog(eq(88L), anyString());
  }
}
