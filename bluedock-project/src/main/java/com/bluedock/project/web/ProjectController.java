package com.bluedock.project.web;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.model.ResultModel;
import com.bluedock.project.service.ProjectMemberService;
import com.bluedock.project.service.ProjectService;
import com.bluedock.project.web.dto.ProjectColumnView;
import com.bluedock.project.web.dto.ProjectInviteView;
import com.bluedock.project.web.dto.ProjectMemberChangeView;
import com.bluedock.project.web.dto.ProjectView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project")
public class ProjectController {
  private final ProjectService projects;
  private final ProjectMemberService members;
  private final ObjectMapper objectMapper;

  public ProjectController(
      ProjectService projects, ProjectMemberService members, ObjectMapper objectMapper) {
    this.projects = projects;
    this.members = members;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/lists")
  public ResultModel<List<ProjectView>> lists(
      @RequestParam(required = false) String archived,
      @RequestParam(required = false, defaultValue = "false") boolean includeArchived,
      @RequestParam(required = false, defaultValue = "all") String type,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String keys) {
    String arch = archived;
    if (arch == null || arch.isBlank()) {
      arch = includeArchived ? "all" : "no";
    }
    return ResultModel.ok(projects.lists(arch, type, name, keys));
  }

  @GetMapping("/one")
  public ResultModel<ProjectView> one(@RequestParam long projectId) {
    return ResultModel.ok(projects.one(projectId));
  }

  @GetMapping("/add")
  public ResultModel<ProjectView> add(
      @RequestParam String name,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) Integer isPersonal,
      @RequestParam(required = false) String columns) {
    return ResultModel.ok(projects.add(name, description, isPersonal, columns));
  }

  @GetMapping("/update")
  public ResultModel<ProjectView> update(
      @RequestParam long projectId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String archiveMethod,
      @RequestParam(required = false) Integer archiveDays,
      @RequestParam(required = false) String aiAutoAnalyze,
      @RequestParam(required = false) String taskTemplateShare,
      @RequestParam(required = false) String departmentOwnerView) {
    return ResultModel.ok(
        projects.update(
            projectId,
            name,
            description,
            archiveMethod,
            archiveDays,
            aiAutoAnalyze,
            taskTemplateShare,
            departmentOwnerView));
  }

  @PostMapping("/user")
  public ResultModel<ProjectMemberChangeView> user(
      @RequestParam long projectId,
      @RequestParam(required = false) String userIds,
      @RequestParam(required = false) String removeUserIds) {
    return ResultModel.ok(members.updateMembers(projectId, userIds, removeUserIds));
  }

  @GetMapping("/invite")
  public ResultModel<ProjectInviteView> invite(@RequestParam long projectId) {
    return ResultModel.ok(members.invite(projectId));
  }

  @GetMapping("/invite/info")
  public ResultModel<ProjectView> inviteInfo(@RequestParam String code) {
    return ResultModel.ok(members.inviteInfo(code));
  }

  @GetMapping("/invite/join")
  public ResultModel<ProjectView> inviteJoin(@RequestParam String code) {
    return ResultModel.ok(members.inviteJoin(code));
  }

  @GetMapping("/transfer")
  public ResultModel<ProjectView> transfer(
      @RequestParam long projectId, @RequestParam long userId) {
    return ResultModel.ok(members.transfer(projectId, userId));
  }

  @PostMapping("/addDeputy")
  public ResultModel<ProjectMemberChangeView> addDeputy(
      @RequestParam long projectId, @RequestParam long userId) {
    return ResultModel.ok(members.addDeputy(projectId, userId));
  }

  @PostMapping("/deleteDeputy")
  public ResultModel<ProjectMemberChangeView> delDeputy(
      @RequestParam long projectId, @RequestParam long userId) {
    return ResultModel.ok(members.delDeputy(projectId, userId));
  }

  @GetMapping("/exit")
  public ResultModel<Void> exit(@RequestParam long projectId) {
    members.exit(projectId);
    return ResultModel.ok();
  }

  @GetMapping("/archived")
  public ResultModel<ProjectView> archived(
      @RequestParam long projectId,
      @RequestParam(required = false, defaultValue = "add") String type) {
    return ResultModel.ok(members.archive(projectId, type));
  }

  @GetMapping("/remove")
  public ResultModel<Void> remove(@RequestParam long projectId) {
    members.remove(projectId);
    return ResultModel.ok();
  }

  @GetMapping("/column/lists")
  public ResultModel<List<ProjectColumnView>> columnLists(@RequestParam long projectId) {
    return ResultModel.ok(projects.columnLists(projectId));
  }

  @GetMapping("/column/one")
  public ResultModel<ProjectColumnView> columnOne(@RequestParam long columnId) {
    return ResultModel.ok(projects.columnOne(columnId));
  }

  @GetMapping("/column/add")
  public ResultModel<ProjectColumnView> columnAdd(
      @RequestParam long projectId,
      @RequestParam String name,
      @RequestParam(required = false) String color) {
    return ResultModel.ok(projects.columnAdd(projectId, name, color));
  }

  @GetMapping("/column/update")
  public ResultModel<ProjectColumnView> columnUpdate(
      @RequestParam long columnId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String color,
      @RequestParam(required = false) Integer sort) {
    return ResultModel.ok(projects.columnUpdate(columnId, name, color, sort));
  }

  @GetMapping("/column/remove")
  public ResultModel<Void> columnRemove(@RequestParam long columnId) {
    projects.columnRemove(columnId);
    return ResultModel.ok();
  }

  /** 当前用户项目列表拖拽排序；仅影响本人。 */
  @PostMapping("/user/sort")
  public ResultModel<Void> userSort(
      @RequestParam(required = false) String list,
      @RequestBody(required = false) Map<String, Object> body) {
    Object raw = null;
    if (body != null) {
      raw = body.get("list");
    }
    if (raw == null && list != null && !list.isBlank()) {
      try {
        raw = objectMapper.readValue(list, List.class);
      } catch (Exception e) {
        raw = List.of();
      }
    }
    projects.userSort(raw instanceof List<?> l ? l : List.of());
    return ResultModel.ok();
  }

  /** 切换当前用户对项目的置顶。 */
  @GetMapping("/top")
  public ResultModel<Map<String, Object>> top(@RequestParam long projectId) {
    return ResultModel.ok(projects.top(projectId));
  }
}
