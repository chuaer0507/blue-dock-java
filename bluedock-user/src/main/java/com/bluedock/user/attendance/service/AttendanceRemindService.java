package com.bluedock.user.attendance.service;

import com.bluedock.common.attendance.AttendanceLeaveBridge;
import com.bluedock.common.attendance.AttendanceRemindBridge;
import com.bluedock.common.calendar.ChinaPublicHolidays;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.service.AttendanceSettingService;
import com.bluedock.user.attendance.repo.AttendanceRemindRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 签到提醒：上班前打卡提醒（in）与上班后缺卡提醒（exceed）。
 *
 * <p>跳过周末、内置法定放假日；经 {@link AttendanceLeaveBridge} 跳过请假/外出（插件可选）。
 */
@Service
public class AttendanceRemindService {
  private static final Logger log = LoggerFactory.getLogger(AttendanceRemindService.class);
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");
  private static final DateTimeFormatter HM2 = DateTimeFormatter.ofPattern("HH:mm");
  private static final Duration SENT_TTL = Duration.ofHours(36);
  private static final String MESSAGE_IN = "快到上班时间了，别忘了打卡哦";
  private static final String MESSAGE_EXCEED = "上班时间到了，你还没有打卡哦";
  public static final String KIND_IN = "in";
  public static final String KIND_EXCEED = "exceed";

  private final AttendanceSettingService settings;
  private final AttendanceRemindRepository repo;
  private final StringRedisTemplate redis;
  private final ObjectProvider<AttendanceRemindBridge> bridge;
  private final ObjectProvider<AttendanceLeaveBridge> leave;

  public AttendanceRemindService(
      AttendanceSettingService settings,
      AttendanceRemindRepository repo,
      StringRedisTemplate redis,
      ObjectProvider<AttendanceRemindBridge> bridge,
      ObjectProvider<AttendanceLeaveBridge> leave) {
    this.settings = settings;
    this.repo = repo;
    this.redis = redis;
    this.bridge = bridge;
    this.leave = leave;
  }

  public Map<String, Object> runOnce() {
    return runAt(LocalDateTime.now());
  }

  /** 供单测注入时间。 */
  public Map<String, Object> runAt(LocalDateTime now) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("skipped", false);
    Map<String, Object> cfg = settings.loadPublic();
    if (!settings.isOpen(cfg)) {
      out.put("skipped", true);
      out.put("reason", "closed");
      return out;
    }
    LocalDate day = now.toLocalDate();
    if (isWeekend(day)) {
      out.put("skipped", true);
      out.put("reason", "weekend");
      return out;
    }
    if (ChinaPublicHolidays.isHoliday(day)) {
      out.put("skipped", true);
      out.put("reason", "holiday");
      return out;
    }
    AttendanceRemindBridge bot = bridge.getIfAvailable();
    if (bot == null) {
      out.put("skipped", true);
      out.put("reason", "noBridge");
      return out;
    }
    String[] wt = settings.workTime(cfg);
    LocalTime start = parseHm(wt[0]);
    int remindIn = settings.remindIn(cfg);
    int remindExceed = settings.remindExceed(cfg);
    LocalTime t = now.toLocalTime().withSecond(0).withNano(0);

    boolean inWindow = false;
    boolean exceedWindow = false;
    if (remindIn > 0) {
      LocalTime inAt = start.minusMinutes(remindIn);
      // 触发窗口：到达提醒时刻起，至上班前（含整分）
      inWindow = !t.isBefore(inAt) && t.isBefore(start);
    }
    if (remindExceed > 0) {
      LocalTime exceedAt = start.plusMinutes(remindExceed);
      // 上班后 exceed 分钟起，当日持续可触发（幂等靠 Redis）
      exceedWindow = !t.isBefore(exceedAt);
    }
    out.put("inWindow", inWindow);
    out.put("exceedWindow", exceedWindow);
    if (!inWindow && !exceedWindow) {
      out.put("skipped", true);
      out.put("reason", "outsideWindow");
      return out;
    }

    List<Long> candidates = repo.listRemindCandidates(day, 3);
    int sentIn = 0;
    int sentExceed = 0;
    int skippedLeave = 0;
    String dayKey = day.toString();
    AttendanceLeaveBridge leaveFilter = leave.getIfAvailable();
    for (Long userId : candidates) {
      if (userId == null || userId <= 0) {
        continue;
      }
      if (leaveFilter != null && leaveFilter.isAwayOn(userId, day)) {
        skippedLeave++;
        continue;
      }
      if (inWindow && markSent(dayKey, userId, KIND_IN)) {
        if (bot.sendDm(userId, MESSAGE_IN) > 0) {
          sentIn++;
        } else {
          clearSent(dayKey, userId, KIND_IN);
        }
      }
      if (exceedWindow && markSent(dayKey, userId, KIND_EXCEED)) {
        if (bot.sendDm(userId, MESSAGE_EXCEED) > 0) {
          sentExceed++;
        } else {
          clearSent(dayKey, userId, KIND_EXCEED);
        }
      }
    }
    out.put("candidates", candidates.size());
    out.put("skippedLeave", skippedLeave);
    out.put("sentIn", sentIn);
    out.put("sentExceed", sentExceed);
    log.debug(
        "attendance remind day={} candidates={} skippedLeave={} in={} exceed={}",
        dayKey,
        candidates.size(),
        skippedLeave,
        sentIn,
        sentExceed);
    return out;
  }

  private boolean markSent(String day, long userId, String kind) {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.attendanceRemindSent(day, userId, kind), "1", SENT_TTL);
    return Boolean.TRUE.equals(first);
  }

  private void clearSent(String day, long userId, String kind) {
    redis.delete(RedisKeys.attendanceRemindSent(day, userId, kind));
  }

  static boolean isWeekend(LocalDate day) {
    DayOfWeek d = day.getDayOfWeek();
    return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
  }

  static LocalTime parseHm(String raw) {
    try {
      String s = raw == null ? "09:00" : raw.trim();
      if (s.length() <= 5) {
        return LocalTime.parse(s, HM);
      }
      return LocalTime.parse(s.substring(0, 5), HM2);
    } catch (Exception e) {
      return LocalTime.of(9, 0);
    }
  }
}
