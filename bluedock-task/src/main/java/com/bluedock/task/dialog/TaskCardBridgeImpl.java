package com.bluedock.task.dialog;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.project.TaskCardBridge;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import com.bluedock.task.service.TaskRelationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskCardBridgeImpl implements TaskCardBridge {
  private final TaskRepository tasks;
  private final TaskVisibilityUserRepository visibilityUsers;
  private final ProjectAccessService access;
  private final TaskRelationService relations;

  public TaskCardBridgeImpl(
      TaskRepository tasks,
      TaskVisibilityUserRepository visibilityUsers,
      ProjectAccessService access,
      TaskRelationService relations) {
    this.tasks = tasks;
    this.visibilityUsers = visibilityUsers;
    this.access = access;
    this.relations = relations;
  }

  @Override
  public Map<String, Object> buildCard(long taskId, long userId, String note) {
    TaskItem t =
        tasks
            .findActive(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    access.requireMember(t.getProjectId(), userId);
    if (!isVisible(t, userId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND);
    }
    Map<String, Object> card = new LinkedHashMap<>();
    card.put("id", t.getId());
    card.put("taskId", t.getId());
    card.put("projectId", t.getProjectId());
    card.put("columnId", t.getColumnId());
    card.put("parentId", t.getParentId());
    card.put("name", t.getName() == null ? "" : t.getName());
    card.put("color", t.getColor() == null ? "" : t.getColor());
    card.put("description", t.getDescription() == null ? "" : t.getDescription());
    card.put("startAt", t.getStartAt());
    card.put("endAt", t.getEndAt());
    card.put("completeAt", t.getCompleteAt());
    card.put("priorityLevel", t.getPriorityLevel());
    card.put("priorityName", t.getPriorityName() == null ? "" : t.getPriorityName());
    card.put("priorityColor", t.getPriorityColor() == null ? "" : t.getPriorityColor());
    card.put("flowItemId", t.getFlowItemId());
    card.put("flowItemName", t.getFlowItemName() == null ? "" : t.getFlowItemName());
    String n = note == null ? "" : note.trim();
    if (n.length() > 500) {
      n = n.substring(0, 500);
    }
    card.put("note", n);
    return card;
  }

  @Override
  public void linkFromDialogIfTaskGroup(long dialogId, long messageId, long taskId, long userId) {
    List<Long> sources = tasks.listIdsByDialogId(dialogId);
    if (sources.isEmpty()) {
      return;
    }
    for (Long sourceTaskId : sources) {
      try {
        relations.link(sourceTaskId, taskId, dialogId, messageId, userId);
      } catch (BusinessException ignored) {
        // 自关联 / 不可见等静默跳过
      }
    }
  }

  private boolean isVisible(TaskItem t, long userId) {
    TaskItem root = t;
    if (t.getParentId() > 0) {
      root = tasks.findActive(t.getParentId()).orElse(t);
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
}
