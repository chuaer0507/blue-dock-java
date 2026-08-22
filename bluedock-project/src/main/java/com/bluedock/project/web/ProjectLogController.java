package com.bluedock.project.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.project.web.dto.ProjectLogDtos.ProjectLogPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project")
public class ProjectLogController {
  private final ProjectLogService logs;

  public ProjectLogController(ProjectLogService logs) {
    this.logs = logs;
  }

  /** 契约：{@code GET /api/project/log/lists}；{@code taskId} 优先于 {@code projectId}。 */
  @GetMapping("/log/lists")
  public ResultModel<ProjectLogPage> lists(
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) Long taskId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(logs.lists(projectId, taskId, page, pageSize));
  }
}
