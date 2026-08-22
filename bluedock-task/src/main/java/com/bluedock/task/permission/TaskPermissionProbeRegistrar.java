package com.bluedock.task.permission;

import com.bluedock.project.service.ProjectPermissionService;
import com.bluedock.task.repo.TaskRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** 向 project 模块注册任务角色探针，供权限矩阵判定 task_leader / task_assist。 */
@Component
public class TaskPermissionProbeRegistrar {
  private final ProjectPermissionService permissions;
  private final TaskRepository tasks;

  public TaskPermissionProbeRegistrar(ProjectPermissionService permissions, TaskRepository tasks) {
    this.permissions = permissions;
    this.tasks = tasks;
  }

  @PostConstruct
  void register() {
    permissions.registerTaskRoleProbes(
        (taskId, userId) ->
            tasks.listAssignees(taskId).stream()
                .anyMatch(row -> row[0] == userId && row[1] == 1),
        (taskId, userId) ->
            tasks.listAssignees(taskId).stream()
                .anyMatch(row -> row[0] == userId && row[1] == 0));
  }
}
