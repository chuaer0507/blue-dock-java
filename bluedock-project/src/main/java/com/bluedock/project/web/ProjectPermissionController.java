package com.bluedock.project.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.project.service.ProjectPermissionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project")
public class ProjectPermissionController {
  private final ProjectPermissionService permissions;

  public ProjectPermissionController(ProjectPermissionService permissions) {
    this.permissions = permissions;
  }

  @GetMapping("/permission")
  public ResultModel<Map<String, Object>> get(@RequestParam long projectId) {
    return ResultModel.ok(permissions.get(projectId));
  }

  /** 契约为 GET；`permissions` 为 JSON 字符串。 */
  @GetMapping("/permission/update")
  public ResultModel<Map<String, Object>> update(
      @RequestParam long projectId, @RequestParam String permissions) {
    return ResultModel.ok(this.permissions.update(projectId, permissions));
  }
}
