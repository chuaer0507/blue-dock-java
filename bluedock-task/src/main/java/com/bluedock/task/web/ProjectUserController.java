package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.ProjectUserTaskService;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserProjectPage;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskCounts;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/user")
public class ProjectUserController {
  private final ProjectUserTaskService userTasks;

  public ProjectUserController(ProjectUserTaskService userTasks) {
    this.userTasks = userTasks;
  }

  @GetMapping("/counts")
  public ResultModel<UserTaskCounts> counts(
      @RequestParam long userId, @RequestParam(required = false) Integer owner) {
    return ResultModel.ok(userTasks.counts(userId, owner));
  }

  @GetMapping("/tasks")
  public ResultModel<UserTaskPage> tasks(
      @RequestParam long userId,
      @RequestParam(required = false) Integer owner,
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) String keys,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    Integer size = pageSize;
    return ResultModel.ok(userTasks.tasks(userId, owner, projectId, keys, page, size));
  }

  @GetMapping("/projects")
  public ResultModel<UserProjectPage> projects(
      @RequestParam long userId,
      @RequestParam(required = false) String archived,
      @RequestParam(required = false) String keys,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(userTasks.projects(userId, archived, keys, page, pageSize));
  }
}
