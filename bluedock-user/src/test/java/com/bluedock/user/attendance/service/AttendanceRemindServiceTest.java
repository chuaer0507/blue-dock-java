package com.bluedock.user.attendance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.attendance.AttendanceLeaveBridge;
import com.bluedock.common.attendance.AttendanceRemindBridge;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.service.AttendanceSettingService;
import com.bluedock.user.attendance.repo.AttendanceRemindRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceRemindServiceTest {
  @Mock AttendanceSettingService settings;
  @Mock AttendanceRemindRepository repo;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock ObjectProvider<AttendanceRemindBridge> bridgeProvider;
  @Mock ObjectProvider<AttendanceLeaveBridge> leaveProvider;
  @Mock AttendanceRemindBridge bridge;
  @Mock AttendanceLeaveBridge leave;

  AttendanceRemindService service;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
    when(leaveProvider.getIfAvailable()).thenReturn(null);
    when(settings.loadPublic()).thenReturn(Map.of("open", "open"));
    when(settings.isOpen(any())).thenReturn(true);
    when(settings.workTime(any())).thenReturn(new String[] {"09:00", "18:00"});
    when(settings.remindIn(any())).thenReturn(5);
    when(settings.remindExceed(any())).thenReturn(10);
    service = new AttendanceRemindService(settings, repo, redis, bridgeProvider, leaveProvider);
  }

  @Test
  void skipsWhenClosed() {
    when(settings.isOpen(any())).thenReturn(false);
    Map<String, Object> out = service.runAt(LocalDateTime.of(2026, 8, 4, 8, 55));
    assertEquals("closed", out.get("reason"));
    verify(repo, never()).listRemindCandidates(any(), any(Integer.class));
  }

  @Test
  void skipsWeekend() {
    Map<String, Object> out = service.runAt(LocalDateTime.of(2026, 8, 8, 8, 55)); // Saturday
    assertEquals("weekend", out.get("reason"));
  }

  @Test
  void skipsPublicHoliday() {
    // 2026-05-01 周五劳动节放假
    Map<String, Object> out = service.runAt(LocalDateTime.of(2026, 5, 1, 8, 55));
    assertEquals("holiday", out.get("reason"));
    verify(repo, never()).listRemindCandidates(any(), any(Integer.class));
  }

  @Test
  void sendsInRemind() {
    LocalDate day = LocalDate.of(2026, 8, 4); // Tuesday
    when(repo.listRemindCandidates(day, 3)).thenReturn(List.of(9L));
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(bridge.sendDm(9L, "快到上班时间了，别忘了打卡哦")).thenReturn(100L);

    Map<String, Object> out = service.runAt(LocalDateTime.of(day, LocalTime.of(8, 55)));
    assertEquals(1, out.get("sentIn"));
    assertEquals(0, out.get("sentExceed"));
    verify(bridge).sendDm(9L, "快到上班时间了，别忘了打卡哦");
    verify(values)
        .setIfAbsent(
            eq(RedisKeys.attendanceRemindSent("2026-08-04", 9L, "in")),
            eq("1"),
            any(Duration.class));
  }

  @Test
  void sendsExceedRemind() {
    LocalDate day = LocalDate.of(2026, 8, 4);
    when(repo.listRemindCandidates(day, 3)).thenReturn(List.of(9L));
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    when(bridge.sendDm(eq(9L), anyString())).thenReturn(101L);

    Map<String, Object> out = service.runAt(LocalDateTime.of(day, LocalTime.of(9, 10)));
    assertTrue((Boolean) out.get("exceedWindow"));
    assertEquals(1, out.get("sentExceed"));
    verify(bridge).sendDm(9L, "上班时间到了，你还没有打卡哦");
  }

  @Test
  void outsideWindowSkips() {
    Map<String, Object> out =
        service.runAt(LocalDateTime.of(2026, 8, 4, 7, 0));
    assertEquals("outsideWindow", out.get("reason"));
    verify(repo, never()).listRemindCandidates(any(), any(Integer.class));
  }

  @Test
  void idempotentSkipSecondSend() {
    LocalDate day = LocalDate.of(2026, 8, 4);
    when(repo.listRemindCandidates(day, 3)).thenReturn(List.of(9L));
    when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

    Map<String, Object> out = service.runAt(LocalDateTime.of(day, LocalTime.of(8, 55)));
    assertEquals(0, out.get("sentIn"));
    verify(bridge, never()).sendDm(anyLong(), anyString());
  }

  @Test
  void skipsUserOnLeave() {
    LocalDate day = LocalDate.of(2026, 8, 4);
    when(leaveProvider.getIfAvailable()).thenReturn(leave);
    when(leave.isAwayOn(9L, day)).thenReturn(true);
    when(repo.listRemindCandidates(day, 3)).thenReturn(List.of(9L));

    Map<String, Object> out = service.runAt(LocalDateTime.of(day, LocalTime.of(8, 55)));
    assertEquals(1, out.get("skippedLeave"));
    assertEquals(0, out.get("sentIn"));
    verify(bridge, never()).sendDm(anyLong(), anyString());
    verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
  }
}
