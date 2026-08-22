package com.bluedock.task.dialog;

import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 按任务可见性计算任务群成员，并在已有 dialog 时同步。
 *
 * <ul>
 *   <li>visibility=1：全体项目成员
 *   <li>visibility=2：任务负责人 + 协助人
 *   <li>visibility=3：负责人 + 协助人 + {@code bluedock_task_visibility_users}
 * </ul>
 */
@Component
public class TaskDialogMembership {
  private final TaskRepository tasks;
  private final TaskVisibilityUserRepository visibilityUsers;
  private final ProjectRepository projects;
  private final TaskGroupBridge groupBridge;

  public TaskDialogMembership(
      TaskRepository tasks,
      TaskVisibilityUserRepository visibilityUsers,
      ProjectRepository projects,
      @Autowired(required = false) TaskGroupBridge groupBridge) {
    this.tasks = tasks;
    this.visibilityUsers = visibilityUsers;
    this.projects = projects;
    this.groupBridge = groupBridge;
  }

  public Set<Long> resolveMembers(TaskItem mainTask) {
    Set<Long> members = new HashSet<>();
    if (mainTask == null || mainTask.getParentId() != 0) {
      return members;
    }
    long taskId = mainTask.getId();
    members.addAll(tasks.listAssigneeUserIds(taskId));
    if (mainTask.getUserId() > 0) {
      members.add(mainTask.getUserId());
    }
    int vis = mainTask.getVisibility();
    if (vis <= 1) {
      members.addAll(projects.listMemberUserIds(mainTask.getProjectId()));
    } else if (vis == 3) {
      members.addAll(visibilityUsers.listUserIds(taskId));
    }
    members.removeIf(id -> id == null || id <= 0);
    return members;
  }

  public Set<Long> resolveMembers(long taskId) {
    return tasks.findActive(taskId).filter(t -> t.getParentId() == 0).map(this::resolveMembers).orElseGet(HashSet::new);
  }

  /** 已有任务群时按可见性重同步成员；无群或无桥则跳过。 */
  public void syncIfPresent(TaskItem mainTask) {
    if (groupBridge == null || mainTask == null || mainTask.getParentId() != 0) {
      return;
    }
    if (mainTask.getDialogId() <= 0) {
      return;
    }
    Collection<Long> members = resolveMembers(mainTask);
    groupBridge.ensureGroup(
        mainTask.getId(), mainTask.getName(), mainTask.getUserId(), members);
  }
}
