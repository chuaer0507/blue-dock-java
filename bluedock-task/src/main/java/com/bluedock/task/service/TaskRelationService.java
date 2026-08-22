package com.bluedock.task.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.domain.TaskRelation;
import com.bluedock.task.repo.TaskRelationRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.TaskRelatedListView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRelationService {
  private final TaskRepository tasks;
  private final TaskRelationRepository relations;
  private final ProjectAccessService access;
  private final ProjectRepository projects;
  private final ProjectColumnRepository columns;

  public TaskRelationService(
      TaskRepository tasks,
      TaskRelationRepository relations,
      ProjectAccessService access,
      ProjectRepository projects,
      ProjectColumnRepository columns) {
    this.tasks = tasks;
    this.relations = relations;
    this.access = access;
    this.projects = projects;
    this.columns = columns;
  }

  public TaskRelatedListView list(long taskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t =
        tasks
            .findActive(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);

    List<TaskRelation> rows = relations.listByTask(taskId, 100);
    Map<Long, Map<String, Object>> byRelated = new LinkedHashMap<>();
    for (TaskRelation r : rows) {
      Map<String, Object> item =
          byRelated.computeIfAbsent(
              r.getRelatedTaskId(),
              id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("relatedTaskId", id);
                m.put("mention", false);
                m.put("mentionedBy", false);
                m.put("latestAt", null);
                m.put("latestMessageId", 0L);
                m.put("task", taskBrief(id));
                return m;
              });
      if (TaskRelation.DIRECTION_MENTION.equals(r.getDirection())) {
        item.put("mention", true);
      }
      if (TaskRelation.DIRECTION_MENTIONED_BY.equals(r.getDirection())) {
        item.put("mentionedBy", true);
      }
      if (r.getUpdatedAt() != null) {
        Object prev = item.get("latestAt");
        if (prev == null
            || (prev instanceof java.time.LocalDateTime lat && r.getUpdatedAt().isAfter(lat))) {
          item.put("latestAt", r.getUpdatedAt());
          item.put("latestMessageId", r.getMessageId());
        }
      }
    }
    List<Map<String, Object>> items = new ArrayList<>(byRelated.values());
    items.removeIf(m -> m.get("task") == null);
    return new TaskRelatedListView(taskId, items);
  }

  /**
   * 建立双向关联（消息 @# 或手动）。{@code dialogId}/{@code messageId} 可为 0。
   *
   * @return true 若新建或更新成功
   */
  @Transactional
  public boolean link(
      long sourceTaskId, long targetTaskId, long dialogId, long messageId, long operatorUserId) {
    if (sourceTaskId == targetTaskId) {
      return false;
    }
    TaskItem source =
        tasks
            .findActive(sourceTaskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    TaskItem target =
        tasks
            .findActive(targetTaskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(source.getProjectId(), operatorUserId);
    access.requireMember(target.getProjectId(), operatorUserId);

    relations.upsert(
        IdGenerator.nextId(),
        sourceTaskId,
        targetTaskId,
        TaskRelation.DIRECTION_MENTION,
        dialogId,
        messageId,
        operatorUserId);
    relations.upsert(
        IdGenerator.nextId(),
        targetTaskId,
        sourceTaskId,
        TaskRelation.DIRECTION_MENTIONED_BY,
        dialogId,
        messageId,
        operatorUserId);
    return true;
  }

  @Transactional
  public Map<String, Object> add(long taskId, long relatedTaskId) {
    long userId = AuthContext.requireUserId();
    if (!link(taskId, relatedTaskId, 0L, 0L, userId)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_RELATION_SELF);
    }
    return Map.of("taskId", taskId, "relatedTaskId", relatedTaskId);
  }

  @Transactional
  public void delete(long taskId, long relatedTaskId) {
    long userId = AuthContext.requireUserId();
    TaskItem t =
        tasks
            .findActive(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    int n = relations.deletePair(taskId, relatedTaskId);
    if (n == 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_RELATION_NOT_FOUND);
    }
  }

  private Map<String, Object> taskBrief(long taskId) {
    return tasks
        .findActive(taskId)
        .map(
            t -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("id", t.getId());
              m.put("name", t.getName());
              m.put("projectId", t.getProjectId());
              m.put(
                  "projectName",
                  projects.findActive(t.getProjectId()).map(Project::getName).orElse(""));
              m.put("columnId", t.getColumnId());
              m.put(
                  "columnName",
                  columns.findActive(t.getColumnId()).map(ProjectColumn::getName).orElse(""));
              m.put(
                  "completeAt",
                  t.getCompleteAt() == null ? null : t.getCompleteAt().toString());
              m.put(
                  "archivedAt",
                  t.getArchivedAt() == null ? null : t.getArchivedAt().toString());
              return m;
            })
        .orElse(null);
  }
}
