package com.bluedock.task.dialog;

import com.bluedock.common.project.TaskDialogAccessBridge;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import org.springframework.stereotype.Component;

@Component
public class TaskDialogAccessBridgeImpl implements TaskDialogAccessBridge {
  private final TaskRepository tasks;
  private final TaskVisibilityUserRepository visibilityUsers;
  private final ProjectRepository projects;

  public TaskDialogAccessBridgeImpl(
      TaskRepository tasks,
      TaskVisibilityUserRepository visibilityUsers,
      ProjectRepository projects) {
    this.tasks = tasks;
    this.visibilityUsers = visibilityUsers;
    this.projects = projects;
  }

  @Override
  public boolean canAccessTaskDialog(long taskId, long userId) {
    if (taskId <= 0 || userId <= 0) {
      return false;
    }
    TaskItem t = tasks.findActive(taskId).orElse(null);
    if (t == null) {
      return false;
    }
    if (t.getParentId() != 0) {
      return false;
    }
    if (projects.findMemberOwner(t.getProjectId(), userId).isEmpty()) {
      return false;
    }
    return isVisible(t, userId);
  }

  private boolean isVisible(TaskItem t, long userId) {
    int vis = t.getVisibility();
    if (vis <= 1) {
      return true;
    }
    if (tasks.isAssignee(t.getId(), userId)) {
      return true;
    }
    return vis == 3 && visibilityUsers.exists(t.getId(), userId);
  }
}
