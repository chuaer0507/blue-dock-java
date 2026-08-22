package com.bluedock.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.ProjectLog;
import com.bluedock.project.repo.ProjectLogRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectLogDtos.PageMeta;
import com.bluedock.project.web.dto.ProjectLogDtos.ProjectLogPage;
import com.bluedock.project.web.dto.ProjectLogDtos.ProjectLogTaskBrief;
import com.bluedock.project.web.dto.ProjectLogDtos.ProjectLogTime;
import com.bluedock.project.web.dto.ProjectLogDtos.ProjectLogView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectLogService {
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;
  private static final DateTimeFormatter HI = DateTimeFormatter.ofPattern("HH:mm");
  private static final String[] WEEKS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

  private final ProjectLogRepository logs;
  private final ProjectRepository projects;
  private final ProjectAccessService access;
  private final ObjectMapper objectMapper;

  public ProjectLogService(
      ProjectLogRepository logs,
      ProjectRepository projects,
      ProjectAccessService access,
      ObjectMapper objectMapper) {
    this.logs = logs;
    this.projects = projects;
    this.access = access;
    this.objectMapper = objectMapper;
  }

  /** 项目级日志（列表页可见；taskOnly=0）。 */
  @Transactional
  public void recordProject(long projectId, long columnId, String detail, Map<String, Object> record) {
    write(projectId, columnId, 0L, 0, detail, record, AuthContext.requireUserId());
  }

  /**
   * 任务日志。{@code detail} 中 {@code {任务}} 按是否子任务替换为「任务」/「子任务」。 主任务 id 写入
   * {@code task_id}；子任务额外写入 {@code record.subtask}。
   */
  @Transactional
  public void recordTask(
      long projectId,
      long columnId,
      long taskId,
      long parentId,
      String taskName,
      String detail,
      Map<String, Object> record,
      int taskOnly) {
    long logTaskId = parentId > 0 ? parentId : taskId;
    String text = detail == null ? "" : detail.replace("{任务}", parentId > 0 ? "子任务" : "任务");
    Map<String, Object> rec = record == null ? new LinkedHashMap<>() : new LinkedHashMap<>(record);
    if (parentId > 0) {
      Map<String, Object> sub = new LinkedHashMap<>();
      sub.put("id", taskId);
      sub.put("parentId", parentId);
      sub.put("name", taskName == null ? "" : taskName);
      rec.put("subtask", sub);
    }
    write(
        projectId,
        columnId,
        logTaskId,
        taskOnly,
        text,
        rec.isEmpty() ? null : rec,
        AuthContext.requireUserId());
  }

  public ProjectLogPage lists(Long projectId, Long taskId, Integer page, Integer pageSize) {
    long userId = AuthContext.requireUserId();
    long tid = taskId == null ? 0L : taskId;
    long parentId = projectId == null ? 0L : projectId;
    int p = page == null || page < 1 ? 1 : page;
    int size =
        pageSize == null
            ? DEFAULT_PAGE_SIZE
            : Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
    int offset = (p - 1) * size;

    long total;
    List<ProjectLog> rows;
    boolean byTask = tid > 0;
    if (byTask) {
      long taskProjectId =
          logs
              .findTaskProjectId(tid)
              .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
      access.requireMember(taskProjectId, userId);
      total = logs.countByTask(tid);
      rows = logs.listByTask(tid, offset, size);
    } else if (parentId > 0) {
      access.requireMember(parentId, userId);
      if (projects.findActive(parentId).isEmpty()) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND);
      }
      total = logs.countByProject(parentId);
      rows = logs.listByProject(parentId, offset, size);
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_LOG_PARAM_REQUIRED);
    }

    int totalPage = total == 0 ? 0 : (int) ((total + size - 1) / size);
    List<ProjectLogView> items =
        rows.stream().map(log -> toView(log, !byTask)).toList();
    return new ProjectLogPage(items, new PageMeta(p, size, total, totalPage));
  }

  private void write(
      long projectId,
      long columnId,
      long taskId,
      int taskOnly,
      String detail,
      Map<String, Object> record,
      long userId) {
    LocalDateTime now = LocalDateTime.now();
    ProjectLog log = new ProjectLog();
    log.setId(IdGenerator.nextId());
    log.setProjectId(projectId);
    log.setColumnId(columnId);
    log.setTaskId(taskId);
    log.setTaskOnly(taskOnly);
    log.setUserId(userId);
    String d = detail == null ? "" : detail;
    if (d.length() > 500) {
      d = d.substring(0, 500);
    }
    log.setDetail(d);
    log.setRecordJson(toJson(record));
    log.setCreatedAt(now);
    log.setUpdatedAt(now);
    logs.insert(log);
  }

  private ProjectLogView toView(ProjectLog log, boolean includeTask) {
    LocalDateTime at = log.getCreatedAt() == null ? LocalDateTime.now() : log.getCreatedAt();
    ProjectLogTime time = buildTime(at);
    ProjectLogTaskBrief brief = null;
    if (includeTask && log.getTaskId() > 0) {
      brief =
          logs
              .findTaskBrief(log.getTaskId())
              .map(
                  m ->
                      new ProjectLogTaskBrief(
                          ((Number) m.get("id")).longValue(),
                          ((Number) m.get("parentId")).longValue(),
                          String.valueOf(m.get("name"))))
              .orElse(null);
    }
    return new ProjectLogView(
        log.getId(),
        log.getProjectId(),
        log.getColumnId(),
        log.getTaskId(),
        log.getUserId(),
        log.getDetail(),
        parseRecord(log.getRecordJson()),
        time,
        time.ymd(),
        brief,
        log.getCreatedAt());
  }

  private static ProjectLogTime buildTime(LocalDateTime at) {
    LocalDateTime now = LocalDateTime.now();
    String ymd =
        at.getYear() == now.getYear()
            ? String.format("%02d-%02d", at.getMonthValue(), at.getDayOfMonth())
            : String.format("%04d-%02d-%02d", at.getYear(), at.getMonthValue(), at.getDayOfMonth());
    String hi = at.format(HI);
    String week = WEEKS[at.getDayOfWeek().getValue() - 1];
    int hour = at.getHour();
    String segment;
    if (hour < 6) {
      segment = "凌晨";
    } else if (hour < 12) {
      segment = "上午";
    } else if (hour < 14) {
      segment = "中午";
    } else if (hour < 18) {
      segment = "下午";
    } else {
      segment = "晚上";
    }
    return new ProjectLogTime(ymd, hi, week, segment);
  }

  private String toJson(Map<String, Object> record) {
    if (record == null || record.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(record);
    } catch (Exception e) {
      return "{}";
    }
  }

  private Map<String, Object> parseRecord(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new HashMap<>();
    }
  }
}
