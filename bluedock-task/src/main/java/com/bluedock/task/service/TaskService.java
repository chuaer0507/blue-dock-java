package com.bluedock.task.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.domain.ProjectFlowItem;
import com.bluedock.project.domain.ProjectLog;
import com.bluedock.project.permission.ProjectPermissionCodes;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.project.repo.ProjectLogRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectFlowService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.project.service.ProjectPermissionService;
import com.bluedock.project.service.ProjectTagService;
import com.bluedock.task.domain.TaskFile;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.dialog.TaskDialogMembership;
import com.bluedock.task.repo.TaskFileRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskTagRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import com.bluedock.task.web.dto.TaskView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final int MAX_VISIBILITY_USERS = 100;
  private static final int MAX_TASK_TAGS = 10;

  private final TaskRepository tasks;
  private final TaskFileRepository taskFiles;
  private final TaskVisibilityUserRepository visibilityUsers;
  private final TaskTagRepository taskTags;
  private final ProjectAccessService access;
  private final ProjectRepository projects;
  private final ProjectColumnRepository columns;
  private final ProjectFlowRepository flowItems;
  private final ProjectFlowService projectFlows;
  private final ProjectTagService projectTags;
  private final ProjectPermissionService projectPermissions;
  private final ProjectLogService projectLogs;
  private final ProjectLogRepository projectLogRepo;
  private final SearchIndexPublisher searchIndex;
  private final ObjectProvider<BrowseRecorder> browseRecorder;
  private final TaskGroupBridge groupBridge;
  private final TaskDialogMembership dialogMembership;
  private final TaskTemplateService taskTemplates;
  private final TaskContentService taskContents;
  private final ObjectProvider<TaskAiService> taskAi;
  private final ObjectProvider<RealtimeFanoutPublisher> realtimeFanout;
  private final TaskColumnFlowSync columnFlowSync;
  private final ObjectMapper objectMapper;

  public TaskService(
      TaskRepository tasks,
      TaskFileRepository taskFiles,
      TaskVisibilityUserRepository visibilityUsers,
      TaskTagRepository taskTags,
      ProjectAccessService access,
      ProjectRepository projects,
      ProjectColumnRepository columns,
      ProjectFlowRepository flowItems,
      ProjectFlowService projectFlows,
      ProjectTagService projectTags,
      ProjectPermissionService projectPermissions,
      ProjectLogService projectLogs,
      ProjectLogRepository projectLogRepo,
      SearchIndexPublisher searchIndex,
      ObjectProvider<BrowseRecorder> browseRecorder,
      @Autowired(required = false) TaskGroupBridge groupBridge,
      TaskDialogMembership dialogMembership,
      TaskTemplateService taskTemplates,
      TaskContentService taskContents,
      ObjectProvider<TaskAiService> taskAi,
      ObjectProvider<RealtimeFanoutPublisher> realtimeFanout,
      TaskColumnFlowSync columnFlowSync,
      ObjectMapper objectMapper) {
    this.tasks = tasks;
    this.taskFiles = taskFiles;
    this.visibilityUsers = visibilityUsers;
    this.taskTags = taskTags;
    this.access = access;
    this.projects = projects;
    this.columns = columns;
    this.flowItems = flowItems;
    this.projectFlows = projectFlows;
    this.projectTags = projectTags;
    this.projectPermissions = projectPermissions;
    this.projectLogs = projectLogs;
    this.projectLogRepo = projectLogRepo;
    this.searchIndex = searchIndex;
    this.browseRecorder = browseRecorder;
    this.groupBridge = groupBridge;
    this.dialogMembership = dialogMembership;
    this.taskTemplates = taskTemplates;
    this.taskContents = taskContents;
    this.taskAi = taskAi;
    this.realtimeFanout = realtimeFanout;
    this.columnFlowSync = columnFlowSync;
    this.objectMapper = objectMapper;
  }

  public List<TaskView> lists(long projectId, Long columnId, boolean includeArchived) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    return tasks.listByProject(projectId, columnId, includeArchived, userId).stream()
        .map(this::toView)
        .toList();
  }

  public TaskView one(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    BrowseRecorder recorder = browseRecorder.getIfAvailable();
    if (recorder != null) {
      recorder.recordTask(userId, taskId);
    }
    return toView(t);
  }

  /**
   * 创建或获取任务群；仅主任务。成员含负责人与项目成员中的当前操作者（至少保证可聊）。
   * 契约：{@code GET /api/project/task/dialog}。
   */
  @Transactional
  public TaskView dialog(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (t.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_NESTED);
    }
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    if (groupBridge == null) {
      return toView(t);
    }
    Set<Long> members = dialogMembership.resolveMembers(t);
    members.add(userId);
    long dialogId = groupBridge.ensureGroup(taskId, t.getName(), t.getUserId(), members);
    if (t.getDialogId() != dialogId) {
      tasks.updateDialogId(taskId, dialogId);
      t.setDialogId(dialogId);
    }
    return toView(t);
  }

  @Transactional
  public TaskView add(
      long projectId,
      Long columnId,
      String name,
      String description,
      String color,
      Integer visibility,
      String visibilityUserIds,
      Long ownerUserId,
      String startAt,
      String endAt,
      Integer loop,
      Long templateId) {
    long userId = AuthContext.requireUserId();
    access.requireMember(projectId, userId);
    projectPermissions.require(projectId, userId, ProjectPermissionCodes.TASK_ADD, null);
    long resolvedColumn =
        columnId == null ? defaultColumnId(projectId) : columnId;
    requireColumnInProject(resolvedColumn, projectId);

    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_NAME_LENGTH);
    }
    int vis = visibility == null ? 1 : visibility;
    if (vis < 1 || vis > 3) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_VISIBILITY_INVALID);
    }

    LocalDateTime now = LocalDateTime.now();
    TaskItem t = new TaskItem();
    t.setId(IdGenerator.nextId());
    t.setParentId(0L);
    t.setProjectId(projectId);
    t.setColumnId(resolvedColumn);
    t.setDialogId(0L);
    t.setName(n);
    t.setDescription(description == null ? "" : description.trim());
    t.setColor(color == null ? "" : color.trim());
    t.setVisibility(vis);
    t.setStartAt(parseOptional(startAt));
    t.setEndAt(parseOptional(endAt));
    applyLoop(t, loop);
    t.setSort(tasks.nextSort(projectId, resolvedColumn));
    t.setUserId(userId);
    t.setCreatedAt(now);
    t.setUpdatedAt(now);

    tasks.insert(t);
    long assignee = ownerUserId == null ? userId : ownerUserId;
    access.requireMember(projectId, assignee);
    tasks.insertAssignee(IdGenerator.nextId(), t.getId(), 0L, projectId, assignee, 1);
    if (vis == 3) {
      applyVisibilityUsers(t, visibilityUserIds);
    }
    if (templateId != null && templateId > 0) {
      taskTemplates.recordUsage(templateId, projectId);
    }
    projectLogs.recordTask(
        projectId, resolvedColumn, t.getId(), 0L, t.getName(), "创建{任务}", null, 0);
    publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, t);
    TaskAiService ai = taskAi.getIfAvailable();
    if (ai != null) {
      ai.scheduleAfterCreate(t.getId());
    }
    publishTaskFanout(RealtimeEventTypes.TASK_CREATED, t);
    return toView(t);
  }

  @Transactional
  public TaskView update(
      long taskId,
      String name,
      String description,
      String color,
      Long columnId,
      Integer visibility,
      String visibilityUserIds,
      Integer complete,
      String startAt,
      String endAt,
      Integer priorityLevel,
      String priorityName,
      String priorityColor,
      String content,
      String owner,
      String assist,
      Long flowItemId,
      String tagIds,
      Integer loop) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    if (complete != null || flowItemId != null) {
      projectPermissions.require(
          t.getProjectId(), userId, ProjectPermissionCodes.TASK_STATUS, t.getId());
    }
    if (startAt != null || endAt != null) {
      projectPermissions.require(
          t.getProjectId(), userId, ProjectPermissionCodes.TASK_TIME, t.getId());
    }
    if (name != null
        || description != null
        || color != null
        || columnId != null
        || visibility != null
        || visibilityUserIds != null
        || priorityLevel != null
        || priorityName != null
        || priorityColor != null
        || content != null
        || owner != null
        || assist != null
        || tagIds != null
        || loop != null) {
      projectPermissions.require(
          t.getProjectId(), userId, ProjectPermissionCodes.TASK_UPDATE, t.getId());
    }

    String oldName = t.getName();
    Long oldColumnId = t.getColumnId();
    LocalDateTime oldComplete = t.getCompleteAt();
    if (name != null) {
      String n = name.trim();
      if (n.isEmpty() || n.length() > 200) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_NAME_LENGTH);
      }
      t.setName(n);
    }
    if (description != null) {
      t.setDescription(description.trim());
    }
    if (color != null) {
      t.setColor(color.trim());
    }
    if (columnId != null) {
      requireColumnInProject(columnId, t.getProjectId());
      t.setColumnId(columnId);
      if (flowItemId == null) {
        columnFlowSync.applyBoundFlowToEntity(t);
      }
    }
    boolean visibilityChanged = false;
    if (visibility != null || visibilityUserIds != null) {
      if (t.getParentId() > 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_VISIBILITY_SUBTASK_FORBIDDEN);
      }
      if (visibility != null) {
        if (visibility < 1 || visibility > 3) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_VISIBILITY_INVALID);
        }
        t.setVisibility(visibility);
        visibilityChanged = true;
      }
      if (t.getVisibility() == 3) {
        if (visibilityUserIds != null) {
          applyVisibilityUsers(t, visibilityUserIds);
        }
      } else if (visibilityChanged) {
        visibilityUsers.deleteByTask(t.getId());
      }
      if (visibilityChanged) {
        tasks.updateChildrenVisibility(t.getId(), t.getVisibility());
      }
    }
    if (complete != null) {
      if (complete == 1) {
        t.setCompleteAt(LocalDateTime.now());
      } else if (complete == 0) {
        t.setCompleteAt(null);
      }
    }
    if (startAt != null) {
      t.setStartAt(startAt.isBlank() ? null : parseOptional(startAt));
    }
    if (endAt != null) {
      t.setEndAt(endAt.isBlank() ? null : parseOptional(endAt));
    }
    if (priorityLevel != null) {
      t.setPriorityLevel(priorityLevel);
    }
    if (priorityName != null) {
      t.setPriorityName(priorityName.trim());
    }
    if (priorityColor != null) {
      t.setPriorityColor(priorityColor.trim());
    }
    if (content != null) {
      String summary = taskContents.save(t, content, userId);
      t.setDescription(summary);
    }

    boolean membersChanged = false;
    LinkedHashSet<Long> ownerIds = null;
    if (owner != null) {
      ownerIds = applyOwners(t, owner);
      membersChanged = true;
    }
    if (assist != null) {
      applyAssists(t, assist, ownerIds);
      membersChanged = true;
    }
    if (flowItemId != null) {
      applyFlowItemWithLog(t, flowItemId);
    }
    if (tagIds != null) {
      applyTagIds(t, tagIds);
    }
    applyLoop(t, loop);

    tasks.update(t);
    if (name != null && !oldName.equals(t.getName())) {
      projectLogs.recordTask(
          t.getProjectId(),
          t.getColumnId(),
          t.getId(),
          t.getParentId(),
          t.getName(),
          "修改{任务}标题",
          Map.of("change", List.of(oldName + " => " + t.getName())),
          0);
    }
    if (columnId != null && !oldColumnId.equals(t.getColumnId())) {
      projectLogs.recordTask(
          t.getProjectId(),
          t.getColumnId(),
          t.getId(),
          t.getParentId(),
          t.getName(),
          "修改{任务}列表",
          Map.of("change", List.of(String.valueOf(oldColumnId) + " => " + t.getColumnId())),
          0);
    }
    boolean newlyCompleted = false;
    if (complete != null || flowItemId != null) {
      boolean wasDone = oldComplete != null;
      boolean nowDone = t.getCompleteAt() != null;
      newlyCompleted = !wasDone && nowDone;
      if (complete != null && wasDone != nowDone) {
        projectLogs.recordTask(
            t.getProjectId(),
            t.getColumnId(),
            t.getId(),
            t.getParentId(),
            t.getName(),
            nowDone ? "标记{任务}完成" : "标记{任务}未完成",
            null,
            0);
      }
    }
    if (content != null) {
      projectLogs.recordTask(
          t.getProjectId(),
          t.getColumnId(),
          t.getId(),
          t.getParentId(),
          t.getName(),
          "修改{任务}详细描述",
          null,
          0);
    }
    if (t.getParentId() == 0) {
      if (groupBridge != null
          && t.getDialogId() > 0
          && (name != null || membersChanged || visibilityChanged || visibilityUserIds != null)) {
        dialogMembership.syncIfPresent(t);
      }
      publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, t);
    }
    publishTaskFanout(RealtimeEventTypes.TASK_UPDATED, t);
    if (newlyCompleted) {
      maybeSpawnRecurring(t, userId);
    }
    return toView(t);
  }

  private LinkedHashSet<Long> applyOwners(TaskItem t, String ownerRaw) {
    LinkedHashSet<Long> owners = new LinkedHashSet<>(parseIdList(ownerRaw));
    if (owners.size() > 10) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_OWNER_LIMIT);
    }
    LinkedHashSet<Long> kept = new LinkedHashSet<>();
    long parentTaskId = t.getParentId() > 0 ? t.getParentId() : t.getId();
    for (Long userId : owners) {
      if (access.findOwner(t.getProjectId(), userId).isEmpty()) {
        continue;
      }
      tasks.insertAssignee(
          IdGenerator.nextId(), t.getId(), parentTaskId, t.getProjectId(), userId, 1);
      kept.add(userId);
    }
    if (kept.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_OWNER_REQUIRED);
    }
    tasks.deleteAssigneesNotIn(t.getId(), 1, kept);
    return kept;
  }

  private void applyAssists(TaskItem t, String assistRaw, LinkedHashSet<Long> ownerIds) {
    if (t.getParentId() > 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_ASSIST_MAIN_ONLY);
    }
    LinkedHashSet<Long> assists = new LinkedHashSet<>(parseIdList(assistRaw));
    if (assists.size() > 10) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_ASSIST_LIMIT);
    }
    Set<Long> owners = ownerIds != null
        ? ownerIds
        : tasks.listAssignees(t.getId()).stream()
            .filter(row -> row[1] == 1)
            .map(row -> row[0])
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    LinkedHashSet<Long> kept = new LinkedHashSet<>();
    for (Long userId : assists) {
      if (owners.contains(userId)) {
        continue;
      }
      if (access.findOwner(t.getProjectId(), userId).isEmpty()) {
        continue;
      }
      tasks.insertAssignee(IdGenerator.nextId(), t.getId(), t.getId(), t.getProjectId(), userId, 0);
      kept.add(userId);
    }
    tasks.deleteAssigneesNotIn(t.getId(), 0, kept);
  }

  @Transactional
  public TaskView addSubtask(long parentId, String name, String description, Long ownerUserId) {
    long userId = AuthContext.requireUserId();
    TaskItem parent = tasks
        .findActive(parentId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_PARENT_NOT_FOUND));
    if (parent.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_NESTED);
    }
    if (parent.getDeletedAt() != null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_ON_DELETED);
    }
    access.requireMember(parent.getProjectId(), userId);
    requireVisible(parent, userId);
    if (tasks.countChildren(parentId) >= 50) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_LIMIT);
    }

    String n = name == null ? "" : name.trim();
    if (n.isEmpty() || n.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_NAME_LENGTH);
    }

    LocalDateTime now = LocalDateTime.now();
    TaskItem t = new TaskItem();
    t.setId(IdGenerator.nextId());
    t.setParentId(parentId);
    t.setProjectId(parent.getProjectId());
    t.setColumnId(parent.getColumnId());
    t.setDialogId(parent.getDialogId());
    t.setName(n);
    t.setDescription(description == null ? "" : description.trim());
    t.setColor("");
    t.setVisibility(parent.getVisibility());
    t.setSort(tasks.countChildren(parentId));
    t.setUserId(userId);
    t.setCreatedAt(now);
    t.setUpdatedAt(now);

    tasks.insert(t);
    long assignee = ownerUserId == null ? userId : ownerUserId;
    access.requireMember(parent.getProjectId(), assignee);
    tasks.insertAssignee(
        IdGenerator.nextId(), t.getId(), parentId, parent.getProjectId(), assignee, 1);
    projectLogs.recordTask(
        t.getProjectId(),
        t.getColumnId(),
        t.getId(),
        parentId,
        t.getName(),
        "创建{任务}",
        null,
        0);
    publishTaskFanout(RealtimeEventTypes.TASK_CREATED, t);
    return toView(t);
  }

  public List<TaskView> subtaskData(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem parent = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(parent.getProjectId(), userId);
    requireVisible(parent, userId);
    return tasks.listByParent(taskId).stream().map(TaskView::from).toList();
  }

  @Transactional
  public TaskView archive(long taskId, boolean follow) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    projectPermissions.require(
        t.getProjectId(), userId, ProjectPermissionCodes.TASK_ARCHIVED, t.getId());
    tasks.archive(taskId, userId);
    if (follow && t.getParentId() == 0) {
      tasks.archiveChildren(taskId, userId);
    }
    t.setArchivedAt(LocalDateTime.now());
    projectLogs.recordTask(
        t.getProjectId(),
        t.getColumnId(),
        t.getId(),
        t.getParentId(),
        t.getName(),
        "归档{任务}",
        null,
        0);
    publishTaskFanout(RealtimeEventTypes.TASK_UPDATED, t);
    return toView(t);
  }

  @Transactional
  public void remove(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    projectPermissions.require(
        t.getProjectId(), userId, ProjectPermissionCodes.TASK_REMOVE, t.getId());
    if (groupBridge != null && t.getParentId() == 0) {
      groupBridge.disbandByLink(taskId);
    }
    tasks.softDelete(taskId, userId);
    if (t.getParentId() == 0) {
      tasks.softDeleteChildren(taskId, userId);
      publishTaskIndex(SearchIndexEvent.ACTION_DELETE, t);
    }
    projectLogs.recordTask(
        t.getProjectId(),
        t.getColumnId(),
        t.getId(),
        t.getParentId(),
        t.getName(),
        "删除{任务}",
        null,
        0);
    publishTaskFanout(RealtimeEventTypes.TASK_DELETED, t);
  }

  /**
   * 换列 / 跨项目移动主任务；子任务一并迁移。契约：{@code GET /api/project/task/move}。
   * 目标列若绑定工作流节点则联动 {@code flowItemId}；可用 {@code completed} 覆盖完成态。
   */
  @Transactional
  public List<TaskView> move(long taskId, long projectId, long columnId, Integer completed) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (t.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_MOVE_SUBTASK_FORBIDDEN);
    }
    access.requireMember(t.getProjectId(), userId);
    access.requireMember(projectId, userId);
    requireVisible(t, userId);
    projectPermissions.require(
        t.getProjectId(), userId, ProjectPermissionCodes.TASK_MOVE, t.getId());
    requireColumnInProject(columnId, projectId);

    if (t.getProjectId() == projectId && t.getColumnId() == columnId) {
      List<TaskView> same = new ArrayList<>();
      same.add(toView(t));
      for (TaskItem child : tasks.listByParent(taskId)) {
        same.add(toView(child));
      }
      return same;
    }

    long oldProject = t.getProjectId();
    long oldColumn = t.getColumnId();
    LocalDateTime oldComplete = t.getCompleteAt();
    t.setProjectId(projectId);
    t.setColumnId(columnId);
    t.setSort(tasks.nextSort(projectId, columnId));
    columnFlowSync.applyBoundFlowToEntity(t);
    if (completed != null) {
      t.setCompleteAt(completed == 1 ? LocalDateTime.now() : null);
    }
    tasks.update(t);
    tasks.updateAssigneesProject(taskId, projectId);
    tasks.updateFilesProject(taskId, projectId);
    if (oldProject != projectId) {
      taskTags.deleteByTask(taskId);
      for (TaskItem child : tasks.listByParent(taskId)) {
        taskTags.deleteByTask(child.getId());
      }
    }
    tasks.moveChildrenLocation(taskId, projectId, columnId);
    columnFlowSync.syncChildrenAfterColumnMove(taskId, projectId, columnId);
    for (TaskItem child : tasks.listByParent(taskId)) {
      tasks.updateAssigneesProject(child.getId(), projectId);
      tasks.updateFilesProject(child.getId(), projectId);
    }
    if (oldProject != projectId || t.getDialogId() > 0) {
      publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, t);
    }
    projectLogs.recordTask(
        projectId,
        columnId,
        t.getId(),
        0L,
        t.getName(),
        "移动{任务}",
        Map.of(
            "change",
            List.of(
                "project " + oldProject + " => " + projectId,
                "column " + oldColumn + " => " + columnId)),
        0);
    publishTaskFanout(RealtimeEventTypes.TASK_UPDATED, t);
    if (oldProject != projectId) {
      // 原项目成员也需感知任务离开
      publishTaskFanoutToProject(RealtimeEventTypes.TASK_DELETED, oldProject, t);
    }
    if (oldComplete == null && t.getCompleteAt() != null) {
      maybeSpawnRecurring(t, userId);
    }
    List<TaskView> out = new ArrayList<>();
    out.add(toView(t));
    for (TaskItem child : tasks.listByParent(taskId)) {
      out.add(toView(child));
    }
    return out;
  }

  /** 子任务升级为主任务。契约：{@code GET /api/project/task/upgrade}。 */
  @Transactional
  public TaskView upgrade(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (t.getParentId() == 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_ALREADY_MAIN);
    }
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    long parentId = t.getParentId();
    tasks
        .findActive(parentId)
        .ifPresent(
            parent -> {
              t.setPriorityLevel(parent.getPriorityLevel());
              t.setPriorityName(parent.getPriorityName());
              t.setPriorityColor(parent.getPriorityColor());
            });
    t.setParentId(0L);
    t.setSort(tasks.nextSort(t.getProjectId(), t.getColumnId()));
    tasks.update(t);
    tasks.updateAssigneeParentTaskId(taskId, taskId);
    publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, t);
    publishTaskFanout(RealtimeEventTypes.TASK_UPDATED, t);
    return toView(t);
  }

  /**
   * 复制主任务到目标项目/列（含子任务与附件元数据）。契约：{@code POST /api/project/task/copy}。
   *
   * @param ownerUserId 可选；缺省时尽量沿用源负责人（须为目标项目成员），否则当前用户
   * @param completed   可选；1/0 覆盖完成态，缺省未完成
   */
  @Transactional
  public TaskView copy(
      long taskId, long projectId, long columnId, Long ownerUserId, Integer completed) {
    long userId = AuthContext.requireUserId();
    TaskItem src = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (src.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_COPY_SUBTASK_FORBIDDEN);
    }
    access.requireMember(src.getProjectId(), userId);
    access.requireMember(projectId, userId);
    requireVisible(src, userId);
    requireColumnInProject(columnId, projectId);

    long owner = resolveCopyOwner(projectId, ownerUserId, src.getId(), userId);
    LocalDateTime now = LocalDateTime.now();
    TaskItem copy = new TaskItem();
    copy.setId(IdGenerator.nextId());
    copy.setParentId(0L);
    copy.setProjectId(projectId);
    copy.setColumnId(columnId);
    copy.setDialogId(0L);
    copy.setName(src.getName());
    copy.setDescription(src.getDescription() == null ? "" : src.getDescription());
    copy.setColor(src.getColor() == null ? "" : src.getColor());
    copy.setVisibility(src.getVisibility());
    copy.setPriorityLevel(src.getPriorityLevel());
    copy.setPriorityName(src.getPriorityName());
    copy.setPriorityColor(src.getPriorityColor());
    copy.setStartAt(src.getStartAt());
    copy.setEndAt(src.getEndAt());
    copy.setLoop(src.getLoop());
    copy.setLoopAt(src.getLoopAt());
    if (completed != null) {
      copy.setCompleteAt(completed == 1 ? now : null);
    } else {
      copy.setCompleteAt(null);
    }
    copy.setSort(tasks.nextSort(projectId, columnId));
    copy.setUserId(userId);
    copy.setCreatedAt(now);
    copy.setUpdatedAt(now);
    tasks.insert(copy);
    tasks.insertAssignee(IdGenerator.nextId(), copy.getId(), 0L, projectId, owner, 1);
    copyAssigneesExceptOwner(src.getId(), copy.getId(), 0L, projectId, owner);
    if (copy.getVisibility() == 3) {
      visibilityUsers.replace(copy.getId(), projectId, visibilityUsers.listUserIds(src.getId()));
    }
    copyFiles(src.getId(), copy.getId(), projectId, now);

    int subSort = 0;
    for (TaskItem child : tasks.listByParent(taskId)) {
      TaskItem sub = new TaskItem();
      sub.setId(IdGenerator.nextId());
      sub.setParentId(copy.getId());
      sub.setProjectId(projectId);
      sub.setColumnId(columnId);
      sub.setDialogId(0L);
      sub.setName(child.getName());
      sub.setDescription(child.getDescription() == null ? "" : child.getDescription());
      sub.setColor(child.getColor() == null ? "" : child.getColor());
      sub.setVisibility(copy.getVisibility());
      sub.setPriorityLevel(child.getPriorityLevel());
      sub.setPriorityName(child.getPriorityName());
      sub.setPriorityColor(child.getPriorityColor());
      sub.setStartAt(child.getStartAt());
      sub.setEndAt(child.getEndAt());
      sub.setCompleteAt(null);
      sub.setSort(subSort++);
      sub.setUserId(userId);
      sub.setCreatedAt(now);
      sub.setUpdatedAt(now);
      tasks.insert(sub);
      long subOwner = resolveCopyOwner(projectId, null, child.getId(), owner);
      tasks.insertAssignee(
          IdGenerator.nextId(), sub.getId(), copy.getId(), projectId, subOwner, 1);
      copyFiles(child.getId(), sub.getId(), projectId, now);
    }

    publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, copy);
    publishTaskFanout(RealtimeEventTypes.TASK_CREATED, copy);
    return toView(copy);
  }

  private long resolveCopyOwner(long projectId, Long ownerUserId, long sourceTaskId, long fallback) {
    if (ownerUserId != null) {
      access.requireMember(projectId, ownerUserId);
      return ownerUserId;
    }
    for (long[] row : tasks.listAssignees(sourceTaskId)) {
      if (row[1] == 1) {
        try {
          access.requireMember(projectId, row[0]);
          return row[0];
        } catch (BusinessException ignored) {
          break;
        }
      }
    }
    return fallback;
  }

  private void copyAssigneesExceptOwner(
      long sourceTaskId, long newTaskId, long parentTaskId, long projectId, long ownerUserId) {
    for (long[] row : tasks.listAssignees(sourceTaskId)) {
      long userId = row[0];
      int ownerFlag = (int) row[1];
      if (ownerFlag == 1 || userId == ownerUserId) {
        continue;
      }
      try {
        access.requireMember(projectId, userId);
      } catch (BusinessException e) {
        continue;
      }
      tasks.insertAssignee(IdGenerator.nextId(), newTaskId, parentTaskId, projectId, userId, 0);
    }
  }

  private void copyFiles(long sourceTaskId, long newTaskId, long projectId, LocalDateTime now) {
    for (TaskFile f : taskFiles.listByTask(sourceTaskId)) {
      TaskFile nf = new TaskFile();
      nf.setId(IdGenerator.nextId());
      nf.setProjectId(projectId);
      nf.setTaskId(newTaskId);
      nf.setName(f.getName());
      nf.setSize(f.getSize());
      nf.setExtension(f.getExtension());
      nf.setPath(f.getPath());
      nf.setThumbnail(f.getThumbnail());
      nf.setUserId(f.getUserId());
      nf.setDownloadCount(0);
      nf.setCreatedAt(now);
      nf.setUpdatedAt(now);
      taskFiles.insert(nf);
    }
  }

  private void publishTaskIndex(String action, TaskItem t) {
    SearchIndexEvent event = new SearchIndexEvent(
        UUID.randomUUID().toString().replace("-", ""),
        action,
        SearchIndexEvent.TYPE_TASK,
        t.getId(),
        t.getUserId(),
        t.getProjectId(),
        t.getName(),
        t.getDescription());
    searchIndex.publish(event);
  }

  private void publishTaskFanout(String type, TaskItem t) {
    publishTaskFanoutToProject(type, t.getProjectId(), t);
  }

  private void publishTaskFanoutToProject(String type, long projectId, TaskItem t) {
    RealtimeFanoutPublisher publisher = realtimeFanout.getIfAvailable();
    if (publisher == null) {
      return;
    }
    List<Long> userIds = access.listMemberUserIds(projectId);
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("projectId", projectId);
    data.put("taskId", t.getId());
    data.put("columnId", t.getColumnId());
    data.put("parentId", t.getParentId());
    data.put("name", t.getName() == null ? "" : t.getName());
    if (RealtimeEventTypes.TASK_DELETED.equals(type)) {
      data.put("deleted", true);
    } else {
      data.put("task", toView(t));
    }
    RealtimeFanoutEvent event =
        new RealtimeFanoutEvent(
            UUID.randomUUID().toString().replace("-", ""), type, List.copyOf(userIds), data);
    publisher.publish(event);
  }

  /** 日历事件：当前用户参与、时间区间相交的主任务。 */
  public List<TaskView> calendar(String start, String end) {
    long userId = AuthContext.requireUserId();
    LocalDateTime s = parseOptional(start);
    LocalDateTime e = parseOptional(end);
    if (s == null || e == null || e.isBefore(s)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.CALENDAR_RANGE_INVALID);
    }
    return tasks.listCalendarForUser(userId, s, e).stream().map(TaskView::from).toList();
  }

  /**
   * 计划时间冲突简表。契约：{@code GET /api/project/task/easyLists}。
   *
   * @param userIds       负责人 id，逗号分隔（兼容参数名 {@code userId}）
   * @param timeRange     可选 {@code start,end}
   * @param excludeTaskId 可选排除任务
   */
  public List<Map<String, Object>> easyLists(
      String userIds, String timeRange, Long excludeTaskId, Integer limit) {
    AuthContext.requireUserId();
    List<Long> owners = parseIdList(userIds);
    if (owners.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_EASY_LIST_USER_IDS);
    }
    LocalDateTime rangeStart = null;
    LocalDateTime rangeEnd = null;
    if (timeRange != null && !timeRange.isBlank()) {
      String[] parts = timeRange.split(",", 2);
      if (parts.length == 2) {
        rangeStart = parseOptional(parts[0].trim());
        rangeEnd = parseOptional(parts[1].trim());
      }
      if (rangeStart == null || rangeEnd == null || rangeEnd.isBefore(rangeStart)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.CALENDAR_RANGE_INVALID);
      }
    }
    int lim = limit == null ? 100 : limit;
    return tasks.listEasy(owners, rangeStart, rangeEnd, excludeTaskId, lim);
  }

  private static List<Long> parseIdList(String raw) {
    List<Long> out = new ArrayList<>();
    if (raw == null || raw.isBlank()) {
      return out;
    }
    for (String part : raw.split("[,|]")) {
      String t = part.trim();
      if (t.isEmpty()) {
        continue;
      }
      try {
        long id = Long.parseLong(t);
        if (id > 0) {
          out.add(id);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    return out;
  }

  private TaskView toView(TaskItem t) {
    List<Long> vis = t.getVisibility() == 3
        ? visibilityUsers.listUserIds(t.getParentId() > 0 ? t.getParentId() : t.getId())
        : List.of();
    List<Long> tags = taskTags.listTagIds(t.getId());
    List<Long> owners = new ArrayList<>();
    List<Long> assists = new ArrayList<>();
    for (long[] row : tasks.listAssignees(t.getId())) {
      if (row == null || row.length < 2) {
        continue;
      }
      if (row[1] == 1) {
        owners.add(row[0]);
      } else {
        assists.add(row[0]);
      }
    }
    return TaskView.from(t, vis, tags, owners, assists);
  }

  private void applyTagIds(TaskItem t, String raw) {
    List<Long> parsed = parseIdList(raw);
    if (parsed.size() > MAX_TASK_TAGS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_TAG_LIMIT, MAX_TASK_TAGS);
    }
    List<Long> kept = projectTags.filterValidTagIds(t.getProjectId(), parsed);
    if (kept.size() > MAX_TASK_TAGS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_TAG_LIMIT, MAX_TASK_TAGS);
    }
    taskTags.replace(t.getId(), t.getProjectId(), kept);
  }

  /**
   * 写入或校验循环字段。{@code loop} 为 null 时仅按当前值重算 loopAt（如改了 endAt）。
   */
  private void applyLoop(TaskItem t, Integer loop) {
    if (loop != null) {
      if (!TaskRecurring.isValid(loop)) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_LOOP_INVALID);
      }
      if (t.getParentId() > 0 && loop > TaskRecurring.OFF) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_LOOP_SUBTASK_FORBIDDEN);
      }
      t.setLoop(loop);
    }
    if (t.getLoop() > TaskRecurring.OFF) {
      if (t.getParentId() > 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_LOOP_SUBTASK_FORBIDDEN);
      }
      if (t.getEndAt() == null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_LOOP_END_REQUIRED);
      }
      t.setLoopAt(t.getEndAt());
    } else {
      t.setLoop(TaskRecurring.OFF);
      t.setLoopAt(null);
    }
  }

  /**
   * 主任务刚标记完成且启用循环时，生成下一周期主任务（不含附件/子任务/富文本）。
   * 项目已删或已归档时跳过。
   */
  private void maybeSpawnRecurring(TaskItem completed, long actorUserId) {
    if (completed.getParentId() != 0
        || completed.getLoop() <= TaskRecurring.OFF
        || completed.getEndAt() == null
        || completed.getArchivedAt() != null) {
      return;
    }
    var project = projects.findActive(completed.getProjectId()).orElse(null);
    if (project == null || project.getArchivedAt() != null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    TaskItem next =
        TaskRecurring.buildNext(
            completed,
            IdGenerator.nextId(),
            actorUserId,
            now,
            tasks.nextSort(completed.getProjectId(), completed.getColumnId()));
    tasks.insert(next);
    boolean hasOwner = false;
    for (long[] row : tasks.listAssignees(completed.getId())) {
      long assigneeId = row[0];
      int ownerFlag = (int) row[1];
      try {
        access.requireMember(next.getProjectId(), assigneeId);
      } catch (BusinessException e) {
        continue;
      }
      tasks.insertAssignee(
          IdGenerator.nextId(), next.getId(), 0L, next.getProjectId(), assigneeId, ownerFlag);
      if (ownerFlag == 1) {
        hasOwner = true;
      }
    }
    if (!hasOwner) {
      tasks.insertAssignee(
          IdGenerator.nextId(), next.getId(), 0L, next.getProjectId(), actorUserId, 1);
    }
    if (next.getVisibility() == 3) {
      visibilityUsers.replace(
          next.getId(), next.getProjectId(), visibilityUsers.listUserIds(completed.getId()));
    }
    List<Long> tags = taskTags.listTagIds(completed.getId());
    if (!tags.isEmpty()) {
      taskTags.replace(next.getId(), next.getProjectId(), tags);
    }
    projectLogs.recordTask(
        next.getProjectId(),
        next.getColumnId(),
        next.getId(),
        0L,
        next.getName(),
        "创建{任务}",
        Map.of("change", List.of("loop from " + completed.getId())),
        0);
    publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, next);
    publishTaskFanout(RealtimeEventTypes.TASK_CREATED, next);
  }

  private void requireVisible(TaskItem t, long userId) {
    if (!isVisible(t, userId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND);
    }
  }

  private boolean isVisible(TaskItem t, long userId) {
    TaskItem root = t;
    if (t.getParentId() > 0) {
      root = tasks
          .findActive(t.getParentId())
          .orElse(t);
    }
    int vis = root.getVisibility();
    if (vis <= 1) {
      return true;
    }
    if (tasks.isAssignee(root.getId(), userId)) {
      return true;
    }
    return vis == 3 && visibilityUsers.exists(root.getId(), userId);
  }

  /**
   * 任务工作流信息；可选 {@code flowItemId} 执行流转。契约：{@code GET /api/project/task/flow}。
   */
  @Transactional
  public Map<String, Object> flow(long taskId, Long flowItemId) {
    long userId = AuthContext.requireUserId();
    TaskItem t = tasks
        .findActive(taskId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    if (t.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_NESTED);
    }
    if (flowItemId != null) {
      projectPermissions.require(
          t.getProjectId(), userId, ProjectPermissionCodes.TASK_STATUS, t.getId());
      LocalDateTime oldComplete = t.getCompleteAt();
      applyFlowItemWithLog(t, flowItemId);
      tasks.update(t);
      if (t.getParentId() == 0) {
        publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, t);
      }
      publishTaskFanout(RealtimeEventTypes.TASK_UPDATED, t);
      if (oldComplete == null && t.getCompleteAt() != null) {
        maybeSpawnRecurring(t, userId);
      }
    }

    Map<String, Object> out = new java.util.LinkedHashMap<>();
    out.put("taskId", t.getId());
    out.put("flowItemId", t.getFlowItemId());
    out.put("flowItemName", t.getFlowItemName() == null ? "" : t.getFlowItemName());
    out.put("completeAt", t.getCompleteAt());

    List<Map<String, Object>> turns = new ArrayList<>();
    if (t.getFlowItemId() > 0) {
      flowItems
          .findActiveItem(t.getFlowItemId())
          .ifPresent(
              cur -> {
                out.put("status", cur.getStatus());
                out.put("color", cur.getColor() == null ? "" : cur.getColor());
                for (Long tid : parseIdList(cur.getTurns())) {
                  flowItems
                      .findActiveItem(tid)
                      .ifPresent(
                          next -> {
                            Map<String, Object> row = new java.util.LinkedHashMap<>();
                            row.put("id", next.getId());
                            row.put("name", next.getName());
                            row.put("status", next.getStatus());
                            row.put("color", next.getColor() == null ? "" : next.getColor());
                            turns.add(row);
                          });
                }
              });
    } else {
      // 尚未入流：可进入项目任意 start 节点
      for (ProjectFlowItem it : flowItems.listItemsByProject(t.getProjectId())) {
        if (!"start".equalsIgnoreCase(it.getStatus())) {
          continue;
        }
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", it.getId());
        row.put("name", it.getName());
        row.put("status", it.getStatus());
        row.put("color", it.getColor() == null ? "" : it.getColor());
        turns.add(row);
      }
    }
    out.put("turns", turns);
    return out;
  }

  /**
   * 根据项目/任务日志重置工作流状态。契约：{@code GET /api/project/task/resetfromlog?id=}。
   *
   * <p>
   * 仅支持带 {@code record.flow} 的「修改任务状态」日志（流转前快照）。
   */
  @Transactional
  public TaskView resetFromLog(long logId) {
    long userId = AuthContext.requireUserId();
    ProjectLog log = projectLogRepo
        .findById(logId)
        .orElseThrow(
            () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_LOG_NOT_FOUND));
    if (log.getTaskId() <= 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.PROJECT_LOG_NOT_FOUND);
    }
    TaskItem t = tasks
        .findActive(log.getTaskId())
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    requireVisible(t, userId);
    projectPermissions.require(
        t.getProjectId(), userId, ProjectPermissionCodes.TASK_STATUS, t.getId());

    Map<String, Object> record = parseLogRecord(log.getRecordJson());
    Object flowRaw = record.get("flow");
    if (!(flowRaw instanceof Map<?, ?> flowMap) || flowMap.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_LOG_RESET_UNSUPPORTED);
    }

    long targetFlowItemId = longVal(flowMap, "flowItemId", "flow_item_id");
    String oldFlowName = t.getFlowItemName() == null ? "" : t.getFlowItemName();
    ProjectFlowItem restored = null;
    if (targetFlowItemId > 0) {
      restored = projectFlows.requireItemInProject(targetFlowItemId, t.getProjectId());
      t.setFlowItemId(restored.getId());
      t.setFlowItemName(restored.getName());
      if (restored.getColumnId() > 0) {
        requireColumnInProject(restored.getColumnId(), t.getProjectId());
        t.setColumnId(restored.getColumnId());
      }
    } else {
      t.setFlowItemId(0L);
      t.setFlowItemName("");
    }

    if (flowMap.containsKey("completeAt") || flowMap.containsKey("complete_at")) {
      Object c = flowMap.get("completeAt");
      if (c == null || "".equals(c) || Boolean.FALSE.equals(c)) {
        t.setCompleteAt(null);
      } else if (c instanceof LocalDateTime ldt) {
        t.setCompleteAt(ldt);
      } else {
        t.setCompleteAt(parseOptional(String.valueOf(c)));
      }
    }

    Object ownerRaw = flowMap.get("owner");
    if (ownerRaw != null) {
      applyOwners(t, joinIds(ownerRaw));
    }
    Object assistRaw = flowMap.get("assist");
    if (assistRaw != null && t.getParentId() == 0) {
      applyAssists(t, joinIds(assistRaw), null);
    }

    tasks.update(t);
    String newName = restored != null
        ? restored.getName()
        : (t.getFlowItemName() == null ? "" : t.getFlowItemName());
    projectLogs.recordTask(
        t.getProjectId(),
        t.getColumnId(),
        t.getId(),
        t.getParentId(),
        t.getName(),
        "重置{任务}状态",
        Map.of(
            "change",
            List.of(
                oldFlowName.isBlank() ? "-" : oldFlowName, newName.isBlank() ? "-" : newName)),
        0);
    if (t.getParentId() == 0) {
      publishTaskIndex(SearchIndexEvent.ACTION_UPSERT, t);
    }
    publishTaskFanout(RealtimeEventTypes.TASK_UPDATED, t);
    return toView(t);
  }

  /** 流转并写入可回滚快照日志（{@code record.flow}）。 */
  private void applyFlowItemWithLog(TaskItem t, long flowItemId) {
    long beforeId = t.getFlowItemId();
    String beforeName = t.getFlowItemName() == null ? "" : t.getFlowItemName();
    LocalDateTime beforeComplete = t.getCompleteAt();
    Map<String, Object> flowData = new LinkedHashMap<>();
    flowData.put("flowItemId", beforeId);
    flowData.put("flowItemName", beforeName);

    applyFlowItem(t, flowItemId);

    if (beforeId == t.getFlowItemId()
        && Objects.equals(beforeName, t.getFlowItemName() == null ? "" : t.getFlowItemName())
        && Objects.equals(beforeComplete, t.getCompleteAt())) {
      return;
    }
    if (!Objects.equals(beforeComplete, t.getCompleteAt())) {
      flowData.put("completeAt", beforeComplete);
    }
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("flow", flowData);
    record.put(
        "change",
        List.of(
            beforeName.isBlank() ? "-" : beforeName,
            t.getFlowItemName() == null || t.getFlowItemName().isBlank()
                ? "-"
                : t.getFlowItemName()));
    projectLogs.recordTask(
        t.getProjectId(),
        t.getColumnId(),
        t.getId(),
        t.getParentId(),
        t.getName(),
        "修改{任务}状态",
        record,
        0);
  }

  private void applyFlowItem(TaskItem t, long flowItemId) {
    if (flowItemId <= 0) {
      t.setFlowItemId(0L);
      t.setFlowItemName("");
      return;
    }
    ProjectFlowItem target = projectFlows.requireItemInProject(flowItemId, t.getProjectId());
    if (t.getFlowItemId() > 0 && t.getFlowItemId() != flowItemId) {
      ProjectFlowItem cur = flowItems.findActiveItem(t.getFlowItemId()).orElse(null);
      if (cur != null) {
        List<Long> allowed = parseIdList(cur.getTurns());
        if (!allowed.contains(flowItemId)) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_FLOW_TURN_FORBIDDEN);
        }
      }
    }
    t.setFlowItemId(target.getId());
    t.setFlowItemName(target.getName());
    if ("end".equalsIgnoreCase(target.getStatus())) {
      if (t.getCompleteAt() == null) {
        t.setCompleteAt(LocalDateTime.now());
      }
    } else if (t.getCompleteAt() != null) {
      t.setCompleteAt(null);
    }
    if (target.getColumnId() > 0) {
      requireColumnInProject(target.getColumnId(), t.getProjectId());
      t.setColumnId(target.getColumnId());
    }
  }

  private Map<String, Object> parseLogRecord(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {
      });
    } catch (Exception e) {
      return Map.of();
    }
  }

  private static long longVal(Map<?, ?> map, String camel, String snake) {
    Object v = map.containsKey(camel) ? map.get(camel) : map.get(snake);
    if (v == null) {
      return 0L;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(v).trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static String joinIds(Object raw) {
    if (raw instanceof Collection<?> c) {
      return c.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    return String.valueOf(raw);
  }

  private void applyVisibilityUsers(TaskItem t, String raw) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>(parseIdList(raw));
    if (ids.size() > MAX_VISIBILITY_USERS) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.TASK_VISIBILITY_USERS_LIMIT, MAX_VISIBILITY_USERS);
    }
    LinkedHashSet<Long> kept = new LinkedHashSet<>();
    for (Long userId : ids) {
      if (access.findOwner(t.getProjectId(), userId).isEmpty()) {
        continue;
      }
      kept.add(userId);
    }
    visibilityUsers.replace(t.getId(), t.getProjectId(), kept);
  }

  private long defaultColumnId(long projectId) {
    return columns.listByProject(projectId).stream()
        .findFirst()
        .map(ProjectColumn::getId)
        .orElseThrow(
            () -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_COLUMN_MISSING));
  }

  private void requireColumnInProject(long columnId, long projectId) {
    ProjectColumn col = columns
        .findActive(columnId)
        .orElseThrow(() -> new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_COLUMN_MISSING));
    if (col.getProjectId() != projectId) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_COLUMN_MISMATCH);
    }
  }

  private static LocalDateTime parseOptional(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      String v = raw.trim().replace('T', ' ');
      if (v.length() == 10) {
        return LocalDateTime.parse(v + " 00:00:00", DT);
      }
      if (v.length() == 16) {
        return LocalDateTime.parse(v + ":00", DT);
      }
      return LocalDateTime.parse(v, DT);
    } catch (DateTimeParseException ex) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_DATETIME_INVALID);
    }
  }
}
