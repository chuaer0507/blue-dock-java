package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.TaskTemplateService;
import com.bluedock.task.web.dto.TaskTemplateSearchPage;
import com.bluedock.task.web.dto.TaskTemplateView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/task")
public class TaskTemplateController {
  private final TaskTemplateService templates;

  public TaskTemplateController(TaskTemplateService templates) {
    this.templates = templates;
  }

  @GetMapping("/templateList")
  public ResultModel<List<TaskTemplateView>> list(@RequestParam long projectId) {
    return ResultModel.ok(templates.list(projectId));
  }

  @GetMapping("/templateVisible")
  public ResultModel<List<TaskTemplateView>> visible(
      @RequestParam(required = false) Long currentProjectId) {
    return ResultModel.ok(templates.visible(currentProjectId));
  }

  @GetMapping("/templateSearch")
  public ResultModel<TaskTemplateSearchPage> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long currentProjectId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return ResultModel.ok(templates.search(keyword, currentProjectId, page, pageSize));
  }

  @PostMapping("/templateSave")
  public ResultModel<TaskTemplateView> save(
      @RequestParam long projectId,
      @RequestParam(required = false) Long id,
      @RequestParam String name,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String content) {
    return ResultModel.ok(templates.save(projectId, id, name, title, content));
  }

  @PostMapping("/templateSort")
  public ResultModel<Void> sort(
      @RequestParam long projectId, @RequestBody(required = false) Map<String, Object> body) {
    Object list = body == null ? null : body.get("list");
    templates.sort(projectId, list instanceof List<?> l ? l : List.of());
    return ResultModel.ok();
  }

  @GetMapping("/templateDelete")
  public ResultModel<Void> delete(@RequestParam long id) {
    templates.delete(id);
    return ResultModel.ok();
  }

  @GetMapping("/templateDefault")
  public ResultModel<Map<String, Object>> toggleDefault(
      @RequestParam long id, @RequestParam long projectId) {
    return ResultModel.ok(templates.toggleDefault(id, projectId));
  }
}
