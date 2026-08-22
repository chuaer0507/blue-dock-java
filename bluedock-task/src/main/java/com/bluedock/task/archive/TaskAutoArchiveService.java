package com.bluedock.task.archive;

import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.repo.TaskRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 已完成任务自动归档：系统 {@code autoArchive} 或项目 {@code archive_method=custom}。
 *
 * <p>调度直接写库，不经 {@code TaskService.archive}（需登录态）。
 */
@Service
public class TaskAutoArchiveService {
  private static final Logger log = LoggerFactory.getLogger(TaskAutoArchiveService.class);
  private static final long SYSTEM_USER_ID = 0L;
  private static final int BATCH = 100;

  private final TaskRepository tasks;
  private final SystemGeneralSettingService settings;

  public TaskAutoArchiveService(TaskRepository tasks, SystemGeneralSettingService settings) {
    this.tasks = tasks;
    this.settings = settings;
  }

  public Map<String, Object> runOnce() {
    return runAt(LocalDateTime.now());
  }

  public Map<String, Object> runAt(LocalDateTime now) {
    Map<String, Object> out = new LinkedHashMap<>();
    Map<String, Object> cfg = settings.loadRaw();
    boolean systemOpen = "open".equalsIgnoreCase(String.valueOf(cfg.get("autoArchive")));
    int systemDays = toDays(cfg.get("autoArchiveDay"), 30);

    // 下限 1 天，细粒度在循环内按项目策略判断
    List<Map<String, Object>> candidates =
        tasks.listAutoArchiveCandidates(now.minusDays(1), BATCH);
    int archived = 0;
    int skipped = 0;
    for (Map<String, Object> row : candidates) {
      long taskId = ((Number) row.get("id")).longValue();
      LocalDateTime completeAt = (LocalDateTime) row.get("completeAt");
      if (completeAt == null) {
        skipped++;
        continue;
      }
      String method = String.valueOf(row.get("archiveMethod"));
      int days;
      if ("custom".equalsIgnoreCase(method)) {
        days = toDays(row.get("archiveDays"), 30);
      } else {
        if (!systemOpen) {
          skipped++;
          continue;
        }
        days = systemDays;
      }
      days = Math.min(365, Math.max(1, days));
      if (completeAt.isAfter(now.minusDays(days))) {
        skipped++;
        continue;
      }
      try {
        tasks.archive(taskId, SYSTEM_USER_ID);
        tasks.archiveChildren(taskId, SYSTEM_USER_ID);
        archived++;
      } catch (Exception e) {
        log.warn("auto-archive task {} failed: {}", taskId, e.toString());
        skipped++;
      }
    }
    out.put("systemOpen", systemOpen);
    out.put("systemDays", systemDays);
    out.put("candidates", candidates.size());
    out.put("archived", archived);
    out.put("skipped", skipped);
    return out;
  }

  private static int toDays(Object v, int def) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v != null) {
      try {
        return Integer.parseInt(String.valueOf(v).trim());
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return def;
  }
}
