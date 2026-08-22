package com.bluedock.task.remind;

import com.bluedock.common.project.UnclaimedTaskRemindBridge;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.repo.TaskRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 未领取任务提醒：按设定时刻向项目群投递 task-alert。 */
@Service
public class UnclaimedTaskRemindService {
  private static final Logger log = LoggerFactory.getLogger(UnclaimedTaskRemindService.class);
  private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
  private static final int PER_PROJECT = 10;
  private static final Duration DAY_TTL = Duration.ofHours(20);

  private final SystemGeneralSettingService settings;
  private final TaskRepository tasks;
  private final StringRedisTemplate redis;
  private final ObjectProvider<UnclaimedTaskRemindBridge> bridge;

  public UnclaimedTaskRemindService(
      SystemGeneralSettingService settings,
      TaskRepository tasks,
      StringRedisTemplate redis,
      ObjectProvider<UnclaimedTaskRemindBridge> bridge) {
    this.settings = settings;
    this.tasks = tasks;
    this.redis = redis;
    this.bridge = bridge;
  }

  public Map<String, Object> runOnce() {
    return runAt(LocalDateTime.now());
  }

  public Map<String, Object> runAt(LocalDateTime now) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!settings.isUnclaimedTaskReminderOpen()) {
      out.put("skipped", true);
      out.put("reason", "closed");
      return out;
    }
    LocalTime target = parseHm(settings.unclaimedTaskReminderTime());
    LocalTime t = now.toLocalTime().withSecond(0).withNano(0);
    // ±1 分钟窗口
    if (Math.abs(Duration.between(target, t).toMinutes()) > 1) {
      out.put("skipped", true);
      out.put("reason", "outsideWindow");
      return out;
    }
    String day = LocalDate.from(now).toString();
    Boolean first =
        redis.opsForValue().setIfAbsent(RedisKeys.unclaimedTaskRemindSent(day), "1", DAY_TTL);
    if (Boolean.FALSE.equals(first)) {
      out.put("skipped", true);
      out.put("reason", "alreadySent");
      return out;
    }
    UnclaimedTaskRemindBridge bot = bridge.getIfAvailable();
    if (bot == null) {
      redis.delete(RedisKeys.unclaimedTaskRemindSent(day));
      out.put("skipped", true);
      out.put("reason", "noBridge");
      return out;
    }

    List<Map<String, Object>> rows = tasks.listUnclaimedTasks(500);
    Map<Long, List<Map<String, Object>>> byProject = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      long projectId = ((Number) row.get("projectId")).longValue();
      byProject.computeIfAbsent(projectId, k -> new ArrayList<>());
      List<Map<String, Object>> list = byProject.get(projectId);
      if (list.size() < PER_PROJECT) {
        list.add(row);
      }
    }
    int sent = 0;
    int skipped = 0;
    for (List<Map<String, Object>> list : byProject.values()) {
      if (list.isEmpty()) {
        continue;
      }
      long dialogId = ((Number) list.get(0).get("dialogId")).longValue();
      StringBuilder sb = new StringBuilder("【任务待领取】\n");
      for (Map<String, Object> task : list) {
        sb.append("- #").append(task.get("id")).append(' ').append(task.get("name")).append('\n');
      }
      try {
        long msgId = bot.sendToDialog(dialogId, sb.toString().trim());
        if (msgId > 0) {
          sent++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        log.warn("unclaimed remind dialog {} failed: {}", dialogId, e.toString());
        skipped++;
      }
    }
    out.put("projects", byProject.size());
    out.put("sent", sent);
    out.put("skipped", skipped);
    return out;
  }

  private static LocalTime parseHm(String s) {
    try {
      return LocalTime.parse(s, HM);
    } catch (Exception e) {
      return LocalTime.of(9, 0);
    }
  }
}
