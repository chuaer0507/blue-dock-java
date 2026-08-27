package com.bluedock.task.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.project.TaskAiDialogBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.system.ai.AiBotChatService;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.dialog.TaskDialogMembership;
import com.bluedock.task.domain.TaskAiEvent;
import com.bluedock.task.domain.TaskContent;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskAiEventRepository;
import com.bluedock.task.repo.TaskAiEventRepository.MemberLoad;
import com.bluedock.task.repo.TaskAiEventRepository.SimilarTask;
import com.bluedock.task.repo.TaskContentRepository;
import com.bluedock.task.repo.TaskRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TaskAiService {
  private static final Logger log = LoggerFactory.getLogger(TaskAiService.class);

  private final TaskRepository tasks;
  private final TaskAiEventRepository aiEvents;
  private final TaskContentRepository contents;
  private final ProjectRepository projects;
  private final ProjectColumnRepository columns;
  private final ProjectAccessService access;
  private final ProjectLogService projectLogs;
  private final TaskRelationService relations;
  private final SystemGeneralSettingService systemSettings;
  private final ObjectMapper objectMapper;
  private final TaskAiDialogBridge dialogBridge;
  private final TaskDialogMembership dialogMembership;
  private final AiBotChatService aiBotChat;

  public TaskAiService(
      TaskRepository tasks,
      TaskAiEventRepository aiEvents,
      TaskContentRepository contents,
      ProjectRepository projects,
      ProjectColumnRepository columns,
      ProjectAccessService access,
      ProjectLogService projectLogs,
      TaskRelationService relations,
      SystemGeneralSettingService systemSettings,
      ObjectMapper objectMapper,
      @Autowired(required = false) TaskAiDialogBridge dialogBridge,
      TaskDialogMembership dialogMembership,
      @Autowired(required = false) AiBotChatService aiBotChat) {
    this.tasks = tasks;
    this.aiEvents = aiEvents;
    this.contents = contents;
    this.projects = projects;
    this.columns = columns;
    this.access = access;
    this.projectLogs = projectLogs;
    this.relations = relations;
    this.systemSettings = systemSettings;
    this.objectMapper = objectMapper;
    this.dialogBridge = dialogBridge;
    this.dialogMembership = dialogMembership;
    this.aiBotChat = aiBotChat;
  }

  /** 任务创建后：系统+项目开启 AI 自动分析时，提交后异步分析。 */
  public void scheduleAfterCreate(long taskId) {
    if (!systemSettings.isTaskAiAutoAnalyzeOpen()) {
      return;
    }
    Runnable run = () -> {
      try {
        analyzeInternal(taskId, false);
      } catch (Exception ignored) {
        // 后台建议失败不影响创建主路径
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              run.run();
            }
          });
    } else {
      run.run();
    }
  }

  /**
   * 定时扫描：新建未分析 / failed 可重试的主任务（系统开关关闭则跳过）。
   *
   * <p>创建后延迟 {@code delaySeconds}，仅扫近 {@code lookbackDays} 天。
   */
  public Map<String, Object> scanPending(int delaySeconds, int lookbackDays, int batchSize) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!systemSettings.isTaskAiAutoAnalyzeOpen()) {
      out.put("skipped", true);
      out.put("reason", "systemClosed");
      return out;
    }
    int delay = Math.max(delaySeconds, 0);
    int days = Math.max(lookbackDays, 1);
    int batch = Math.min(Math.max(batchSize, 1), 20);
    LocalDateTime now = LocalDateTime.now();
    List<Long> ids = new ArrayList<>();
    ids.addAll(aiEvents.listNewTasksWithoutAiEvents(now.minusSeconds(delay), now.minusDays(days), batch));
    int remain = batch - ids.size();
    if (remain > 0) {
      for (Long id : aiEvents.listRetryableFailedTaskIds(remain)) {
        if (!ids.contains(id)) {
          ids.add(id);
        }
      }
    }
    int ok = 0;
    int fail = 0;
    for (Long taskId : ids) {
      try {
        analyzeInternal(taskId, false);
        ok++;
      } catch (Exception e) {
        log.warn("task ai scan {} failed: {}", taskId, e.toString());
        fail++;
      }
    }
    out.put("candidates", ids.size());
    out.put("ok", ok);
    out.put("fail", fail);
    return out;
  }

  /**
   * 手动触发 AI 建议生成。契约 {@code GET|POST /api/project/task/aiGenerate}。
   *
   * <p>优先 {@link AiBotChatService}（OpenAI 兼容）；不可用或解析失败时回退启发式。结果写入 {@code
   * bluedock_task_ai_events}，并经 {@link TaskAiDialogBridge} 投递任务群 Markdown 卡片。
   */
  @Transactional
  public Map<String, Object> generate(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (t.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_NESTED);
    }
    access.requireMember(t.getProjectId(), userId);
    return analyzeInternal(taskId, true);
  }

  /** 废弃路由占位：{@code /api/project/ai/generate}。 */
  public Map<String, Object> projectGenerateDeprecated() {
    AuthContext.requireUserId();
    return Map.of("deprecated", true);
  }

  @Transactional
  public Map<String, Object> apply(
      long taskId, long messageId, String type, Long assigneeUserId, Long relatedTaskId) {
    long userId = AuthContext.requireUserId();
    String eventType = requireType(type);
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);

    TaskAiEvent event = aiEvents
        .findByTaskTypeMessage(taskId, eventType, messageId)
        .orElseThrow(
            () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_AI_NOT_FOUND));
    Map<String, Object> result = readResult(event);
    if (result.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_AI_EMPTY);
    }

    aiEvents.markStatus(event.getId(), TaskAiEvent.STATUS_APPLIED);

    if (TaskAiEvent.TYPE_SIMILAR.equals(eventType)
        && relatedTaskId != null
        && relatedTaskId > 0) {
      relations.link(taskId, relatedTaskId, t.getDialogId(), messageId, userId);
      projectLogs.recordTask(
          t.getProjectId(),
          t.getColumnId(),
          taskId,
          0L,
          t.getName(),
          "AI建议：关联任务 #" + relatedTaskId,
          null,
          0);
    } else if (TaskAiEvent.TYPE_ASSIGNEE.equals(eventType)
        && assigneeUserId != null
        && assigneeUserId > 0) {
      projectLogs.recordTask(
          t.getProjectId(),
          t.getColumnId(),
          taskId,
          0L,
          t.getName(),
          "AI建议：指派给 " + assigneeUserId,
          null,
          0);
    } else {
      projectLogs.recordTask(
          t.getProjectId(),
          t.getColumnId(),
          taskId,
          0L,
          t.getName(),
          "AI建议：采纳" + eventType + "建议",
          null,
          0);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("type", eventType);
    out.put("taskId", taskId);
    out.put("result", result);
    out.put(
        "message",
        updateCardStatus(
            t.getDialogId(),
            messageId,
            eventType,
            TaskAiEvent.STATUS_APPLIED,
            assigneeUserId == null ? 0L : assigneeUserId,
            relatedTaskId == null ? 0L : relatedTaskId));
    return out;
  }

  @Transactional
  public Map<String, Object> dismiss(
      long taskId, long messageId, String type, Long assigneeUserId, Long relatedTaskId) {
    long userId = AuthContext.requireUserId();
    String eventType = requireType(type);
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);

    TaskAiEvent event = aiEvents
        .findByTaskTypeMessage(taskId, eventType, messageId)
        .orElseThrow(
            () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_AI_NOT_FOUND));
    aiEvents.markStatus(event.getId(), TaskAiEvent.STATUS_DISMISSED);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(
        "message",
        updateCardStatus(
            t.getDialogId(),
            messageId,
            eventType,
            TaskAiEvent.STATUS_DISMISSED,
            assigneeUserId == null ? 0L : assigneeUserId,
            relatedTaskId == null ? 0L : relatedTaskId));
    out.put("type", eventType);
    out.put("taskId", taskId);
    out.put("eventId", event.getId());
    if (assigneeUserId != null) {
      out.put("userId", assigneeUserId);
    }
    if (relatedTaskId != null) {
      out.put("related", relatedTaskId);
    }
    return out;
  }

  private Map<String, Object> analyzeInternal(long taskId, boolean requireAuthContext) {
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (t.getParentId() != 0) {
      return Map.of("taskId", taskId, "events", List.of());
    }
    Project project = projects
        .findActive(t.getProjectId())
        .orElseThrow(
            () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_NOT_FOUND));
    if (!"open".equalsIgnoreCase(nullToOpen(project.getAiAutoAnalyze())) && !requireAuthContext) {
      return Map.of("taskId", taskId, "events", List.of(), "skipped", "project");
    }

    ensurePendingEvents(taskId);
    String columnName = columns.findActive(t.getColumnId()).map(ProjectColumn::getName).orElse("");
    String contentText = contents.findLatest(taskId).map(TaskContent::getContent).orElse("");
    if (contentText == null || contentText.isBlank()) {
      contentText = t.getDescription() == null ? "" : t.getDescription();
    }

    List<Map<String, Object>> eventViews = new ArrayList<>();
    long sharedMessageId = 0L;
    List<Map<String, Object>> suggestions = new ArrayList<>();

    for (String type : TaskAiEvent.eventTypes()) {
      TaskAiEvent event = aiEvents
          .findByTaskAndType(taskId, type)
          .orElse(null);
      if (event == null) {
        continue;
      }
      if (TaskAiEvent.STATUS_COMPLETED.equals(event.getStatus())
          || TaskAiEvent.STATUS_APPLIED.equals(event.getStatus())
          || TaskAiEvent.STATUS_DISMISSED.equals(event.getStatus())
          || TaskAiEvent.STATUS_SKIPPED.equals(event.getStatus())) {
        eventViews.add(toView(event));
        continue;
      }
      if (TaskAiEvent.STATUS_FAILED.equals(event.getStatus())
          && event.getRetryCount() >= TaskAiEvent.MAX_RETRY) {
        eventViews.add(toView(event));
        continue;
      }
      if (!aiEvents.markProcessing(event.getId())) {
        eventViews.add(toView(aiEvents.findByTaskAndType(taskId, type).orElse(event)));
        continue;
      }

      try {
        if (!shouldExecute(t, type, contentText)) {
          aiEvents.markSkipped(event.getId(), "不满足执行条件");
          eventViews.add(toView(aiEvents.findByTaskAndType(taskId, type).orElse(event)));
          continue;
        }
        Map<String, Object> suggestion = buildSuggestion(t, project, columnName, contentText, type);
        if (suggestion == null) {
          aiEvents.markSkipped(event.getId(), "未生成有效建议");
          eventViews.add(toView(aiEvents.findByTaskAndType(taskId, type).orElse(event)));
          continue;
        }
        String json = objectMapper.writeValueAsString(suggestion);
        aiEvents.markCompleted(event.getId(), json, 0L);
        suggestions.add(suggestion);
        eventViews.add(toView(aiEvents.findByTaskAndType(taskId, type).orElse(event)));
      } catch (Exception ex) {
        aiEvents.markFailed(event.getId(), ex.getMessage(), event.getRetryCount() + 1);
        eventViews.add(toView(aiEvents.findByTaskAndType(taskId, type).orElse(event)));
      }
    }

    if (!suggestions.isEmpty() && dialogBridge != null) {
      Set<Long> members = dialogMembership.resolveMembers(t);
      if (requireAuthContext) {
        try {
          members.add(AuthContext.requireUserId());
        } catch (Exception ignored) {
          // 后台自动分析无 AuthContext
        }
      }
      String markdown = buildCardMarkdown(taskId, suggestions);
      sharedMessageId = dialogBridge.publishSuggestion(
          taskId, t.getName(), t.getUserId(), members, markdown);
      if (sharedMessageId > 0) {
        aiEvents.updateMessageIdForCompleted(taskId, sharedMessageId);
        for (int i = 0; i < eventViews.size(); i++) {
          Map<String, Object> v = eventViews.get(i);
          if (TaskAiEvent.STATUS_COMPLETED.equals(String.valueOf(v.get("status")))
              && (v.get("messageId") == null || ((Number) v.get("messageId")).longValue() == 0L)) {
            v.put("messageId", sharedMessageId);
          }
        }
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("taskId", taskId);
    out.put("messageId", sharedMessageId);
    out.put("suggestions", suggestions);
    out.put("events", eventViews);
    return out;
  }

  private Map<String, Object> updateCardStatus(
      long dialogId, long messageId, String type, String status, long userId, long related) {
    if (dialogBridge == null || messageId <= 0) {
      return null;
    }
    return dialogBridge.updateActionStatus(dialogId, messageId, type, status, userId, related);
  }

  static String buildCardMarkdown(long taskId, List<Map<String, Object>> suggestions) {
    StringBuilder sb = new StringBuilder();
    sb.append("## AI 建议\n\n");
    for (Map<String, Object> s : suggestions) {
      if (s == null) {
        continue;
      }
      String type = String.valueOf(s.getOrDefault("type", ""));
      switch (type) {
        case TaskAiEvent.TYPE_DESCRIPTION -> {
          sb.append("### 描述建议\n");
          sb.append(":::ai-action{type=description task_id=")
              .append(taskId)
              .append(" message_id=0}:::\n");
          sb.append(String.valueOf(s.getOrDefault("content", ""))).append("\n:::\n\n");
        }
        case TaskAiEvent.TYPE_SUBTASKS -> {
          sb.append("### 子任务建议\n");
          sb.append(":::ai-action{type=subtasks task_id=")
              .append(taskId)
              .append(" message_id=0}:::\n");
          Object content = s.get("content");
          if (content instanceof List<?> list) {
            for (Object item : list) {
              sb.append("- ").append(item).append('\n');
            }
          }
          sb.append(":::\n\n");
        }
        case TaskAiEvent.TYPE_ASSIGNEE -> {
          sb.append("### 负责人建议\n");
          Object content = s.get("content");
          if (content instanceof List<?> list) {
            for (Object item : list) {
              if (!(item instanceof Map<?, ?> m)) {
                continue;
              }
              long userId = toLong(m.get("userId"));
              sb.append(":::ai-action{type=assignee task_id=")
                  .append(taskId)
                  .append(" message_id=0 userId=")
                  .append(userId)
                  .append("}:::\n");
              sb.append("**")
                  .append(mapVal(m, "nickname", userId))
                  .append("** — ")
                  .append(mapVal(m, "reason", ""))
                  .append("\n:::\n\n");
            }
          }
        }
        case TaskAiEvent.TYPE_SIMILAR -> {
          sb.append("### 相似任务\n");
          Object content = s.get("content");
          if (content instanceof List<?> list) {
            for (Object item : list) {
              if (!(item instanceof Map<?, ?> m)) {
                continue;
              }
              long related = toLong(m.get("taskId"));
              sb.append(":::ai-action{type=similar task_id=")
                  .append(taskId)
                  .append(" message_id=0 related=")
                  .append(related)
                  .append("}:::\n");
              sb.append("#")
                  .append(related)
                  .append(' ')
                  .append(mapVal(m, "name", ""))
                  .append(" (")
                  .append(mapVal(m, "similarity", ""))
                  .append(")\n:::\n\n");
            }
          }
        }
        default -> {
          // ignore
        }
      }
    }
    return sb.toString().trim();
  }

  private static Object mapVal(Map<?, ?> m, String key, Object fallback) {
    Object v = m.get(key);
    return v == null ? fallback : v;
  }

  private static long toLong(Object v) {
    if (v instanceof Number n) {
      return n.longValue();
    }
    if (v == null) {
      return 0L;
    }
    try {
      return Long.parseLong(String.valueOf(v));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private void ensurePendingEvents(long taskId) {
    LocalDateTime now = LocalDateTime.now();
    for (String type : TaskAiEvent.eventTypes()) {
      if (aiEvents.findByTaskAndType(taskId, type).isPresent()) {
        continue;
      }
      TaskAiEvent e = new TaskAiEvent();
      e.setId(IdGenerator.nextId());
      e.setTaskId(taskId);
      e.setEventType(type);
      e.setStatus(TaskAiEvent.STATUS_PENDING);
      e.setRetryCount(0);
      e.setMessageId(0L);
      e.setCreatedAt(now);
      e.setUpdatedAt(now);
      aiEvents.insert(e);
    }
  }

  private boolean shouldExecute(TaskItem t, String type, String contentText) {
    return switch (type) {
      case TaskAiEvent.TYPE_DESCRIPTION -> {
        String c = contentText == null ? "" : contentText.replaceAll("<[^>]+>", "").trim();
        yield c.isEmpty() || c.length() < 20;
      }
      case TaskAiEvent.TYPE_SUBTASKS ->
        tasks.countChildren(t.getId()) == 0 && codePointLen(t.getName()) > 5;
      case TaskAiEvent.TYPE_ASSIGNEE -> {
        boolean hasOwner = tasks.listAssignees(t.getId()).stream().anyMatch(a -> a[1] == 1);
        yield !hasOwner;
      }
      case TaskAiEvent.TYPE_SIMILAR -> true;
      default -> false;
    };
  }

  private Map<String, Object> buildSuggestion(
      TaskItem t, Project project, String columnName, String contentText, String type) {
    Map<String, Object> fromModel = tryLlmSuggestion(t, project, columnName, contentText, type);
    if (fromModel != null) {
      return fromModel;
    }
    return buildHeuristicSuggestion(t, project, columnName, contentText, type);
  }

  private Map<String, Object> tryLlmSuggestion(
      TaskItem t, Project project, String columnName, String contentText, String type) {
    if (aiBotChat == null || !aiBotChat.available()) {
      return null;
    }
    try {
      String system =
          """
          你是任务协作助手。根据给定任务上下文，为指定建议类型输出严格 JSON（不要 Markdown 代码围栏，不要解释）。
          类型与 JSON 形态：
          - description: {"type":"description","content":"Markdown 描述正文"}
          - subtasks: {"type":"subtasks","content":["子任务1","子任务2",...]}（2～5 条）
          - assignee: {"type":"assignee","content":[{"userId":数字,"nickname":"昵称","reason":"理由"},...]}（只能从候选中选，最多 2 人）
          - similar: {"type":"similar","lang":"zh","content":[{"taskId":数字,"name":"名称","similarity":0到1小数},...]}（只能从候选中选）
          不要编造候选以外的 userId/taskId。
          """;
      String user = buildLlmUserPrompt(t, project, columnName, contentText, type);
      String raw = aiBotChat.chat(system, user);
      if (raw == null || raw.isBlank()) {
        return null;
      }
      return parseLlmSuggestion(raw, type);
    } catch (Exception e) {
      log.debug("task ai llm fallback type={}: {}", type, e.toString());
      return null;
    }
  }

  private String buildLlmUserPrompt(
      TaskItem t, Project project, String columnName, String contentText, String type) {
    StringBuilder sb = new StringBuilder();
    sb.append("建议类型: ").append(type).append('\n');
    sb.append("任务名: ").append(safe(t.getName())).append('\n');
    sb.append("项目: ").append(safe(project.getName())).append('\n');
    sb.append("列: ").append(safe(columnName)).append('\n');
    sb.append("现有描述/正文:\n").append(safe(contentText)).append("\n\n");
    if (TaskAiEvent.TYPE_ASSIGNEE.equals(type)) {
      List<Long> existing = tasks.listAssigneeUserIds(t.getId());
      List<MemberLoad> members = aiEvents.listProjectMemberLoads(t.getProjectId(), existing);
      sb.append("候选人(JSON):\n");
      try {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MemberLoad m : members.stream().limit(8).toList()) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("userId", m.userId());
          row.put("nickname", m.nickname());
          row.put("inProgress", m.inProgress());
          rows.add(row);
        }
        sb.append(objectMapper.writeValueAsString(rows));
      } catch (Exception e) {
        sb.append("[]");
      }
      sb.append('\n');
    } else if (TaskAiEvent.TYPE_SIMILAR.equals(type)) {
      List<SimilarTask> similar =
          aiEvents.findSimilarByName(t.getProjectId(), t.getId(), t.getName(), 5);
      sb.append("相似候选(JSON):\n");
      try {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SimilarTask s : similar) {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("taskId", s.taskId());
          row.put("name", s.name());
          row.put("similarity", s.similarity());
          rows.add(row);
        }
        sb.append(objectMapper.writeValueAsString(rows));
      } catch (Exception e) {
        sb.append("[]");
      }
      sb.append('\n');
    }
    sb.append("请只输出一条 JSON 对象。");
    return sb.toString();
  }

  Map<String, Object> parseLlmSuggestion(String raw, String expectedType) {
    String json = stripJsonFence(raw);
    try {
      Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
      if (parsed == null || parsed.isEmpty()) {
        return null;
      }
      String type = String.valueOf(parsed.getOrDefault("type", "")).trim().toLowerCase(Locale.ROOT);
      if (!expectedType.equals(type)) {
        parsed.put("type", expectedType);
      }
      Object content = parsed.get("content");
      if (content == null) {
        return null;
      }
      if (TaskAiEvent.TYPE_DESCRIPTION.equals(expectedType)) {
        String text = String.valueOf(content).trim();
        if (text.isEmpty()) {
          return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", expectedType);
        out.put("content", text);
        out.put("source", "llm");
        return out;
      }
      if (TaskAiEvent.TYPE_SUBTASKS.equals(expectedType)) {
        if (!(content instanceof List<?> list) || list.isEmpty()) {
          return null;
        }
        List<String> subs = new ArrayList<>();
        for (Object o : list) {
          if (o == null) {
            continue;
          }
          String s = String.valueOf(o).trim();
          if (!s.isEmpty()) {
            subs.add(s);
          }
        }
        if (subs.isEmpty()) {
          return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", expectedType);
        out.put("content", subs);
        out.put("source", "llm");
        return out;
      }
      if (TaskAiEvent.TYPE_ASSIGNEE.equals(expectedType) || TaskAiEvent.TYPE_SIMILAR.equals(expectedType)) {
        if (!(content instanceof List<?> list) || list.isEmpty()) {
          return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", expectedType);
        if (TaskAiEvent.TYPE_SIMILAR.equals(expectedType)) {
          out.put("lang", "zh");
        }
        out.put("content", content);
        out.put("source", "llm");
        return out;
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }

  static String stripJsonFence(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.startsWith("```")) {
      int firstNl = s.indexOf('\n');
      if (firstNl > 0) {
        s = s.substring(firstNl + 1);
      }
      int end = s.lastIndexOf("```");
      if (end >= 0) {
        s = s.substring(0, end);
      }
      s = s.trim();
    }
    int start = s.indexOf('{');
    int end = s.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return s.substring(start, end + 1);
    }
    return s;
  }

  private Map<String, Object> buildHeuristicSuggestion(
      TaskItem t, Project project, String columnName, String contentText, String type) {
    return switch (type) {
      case TaskAiEvent.TYPE_DESCRIPTION -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "description");
        m.put(
            "content",
            """
                ### 目标
                完成「%s」（项目：%s / 列：%s）。

                ### 要求
                - 明确交付物与验收标准
                - 必要时拆分子任务并指定负责人
                """
                .formatted(safe(t.getName()), safe(project.getName()), safe(columnName)));
        m.put("source", "heuristic");
        yield m;
      }
      case TaskAiEvent.TYPE_SUBTASKS -> {
        List<String> subs = heuristicSubtasks(t.getName());
        if (subs.isEmpty()) {
          yield null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "subtasks");
        m.put("content", subs);
        m.put("source", "heuristic");
        yield m;
      }
      case TaskAiEvent.TYPE_ASSIGNEE -> {
        List<Long> existing = tasks.listAssigneeUserIds(t.getId());
        List<MemberLoad> members = aiEvents.listProjectMemberLoads(t.getProjectId(), existing);
        if (members.isEmpty()) {
          yield null;
        }
        List<Map<String, Object>> recs = new ArrayList<>();
        for (MemberLoad m : members.stream().limit(2).toList()) {
          Map<String, Object> r = new LinkedHashMap<>();
          r.put("userId", m.userId());
          r.put("nickname", m.nickname());
          r.put(
              "reason",
              m.inProgress() == 0 ? "当前负载较低" : "进行中 " + m.inProgress() + " 个");
          recs.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "assignee");
        out.put("content", recs);
        out.put("source", "heuristic");
        yield out;
      }
      case TaskAiEvent.TYPE_SIMILAR -> {
        List<SimilarTask> similar = aiEvents.findSimilarByName(t.getProjectId(), t.getId(), t.getName(), 5);
        if (similar.isEmpty()) {
          yield null;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (SimilarTask s : similar) {
          Map<String, Object> item = new LinkedHashMap<>();
          item.put("taskId", s.taskId());
          item.put("name", s.name());
          item.put("similarity", s.similarity());
          items.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "similar");
        out.put("lang", "zh");
        out.put("content", items);
        out.put("source", "heuristic");
        yield out;
      }
      default -> null;
    };
  }

  private static List<String> heuristicSubtasks(String name) {
    String n = name == null ? "" : name.trim();
    if (codePointLen(n) <= 5) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    out.add("澄清需求与验收标准");
    out.add("实现：" + truncate(n, 24));
    out.add("自测并同步结果");
    return out;
  }

  private Map<String, Object> toView(TaskAiEvent e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("taskId", e.getTaskId());
    m.put("eventType", e.getEventType());
    m.put("status", e.getStatus());
    m.put("retryCount", e.getRetryCount());
    m.put("messageId", e.getMessageId());
    m.put("result", readResult(e));
    m.put("error", e.getError() == null ? "" : e.getError());
    return m;
  }

  private Map<String, Object> readResult(TaskAiEvent e) {
    if (e.getResultJson() == null || e.getResultJson().isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(e.getResultJson(), new TypeReference<>() {
      });
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private static String requireType(String type) {
    String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    for (String known : TaskAiEvent.eventTypes()) {
      if (known.equals(t)) {
        return known;
      }
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_AI_TYPE_INVALID);
  }

  private static String nullToOpen(String v) {
    return v == null || v.isBlank() ? "open" : v;
  }

  private static String safe(String s) {
    return s == null ? "" : s.replace("`", "'");
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max);
  }

  private static int codePointLen(String s) {
    return s == null ? 0 : s.codePointCount(0, s.length());
  }
}
