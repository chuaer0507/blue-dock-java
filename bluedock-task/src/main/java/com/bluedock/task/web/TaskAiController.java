package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.TaskAiService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/project/task")
public class TaskAiController {
  private final TaskAiService ai;

  public TaskAiController(TaskAiService ai) {
    this.ai = ai;
  }

  @GetMapping("/aiGenerate")
  public ResultModel<Map<String, Object>> generateGet(@RequestParam long taskId) {
    return ResultModel.ok(ai.generate(taskId));
  }

  @PostMapping("/aiGenerate")
  public ResultModel<Map<String, Object>> generatePost(@RequestParam long taskId) {
    return ResultModel.ok(ai.generate(taskId));
  }

  @PostMapping("/aiApply")
  public ResultModel<Map<String, Object>> apply(
      @RequestParam long taskId,
      @RequestParam long messageId,
      @RequestParam String type,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long related) {
    return ResultModel.ok(ai.apply(taskId, messageId, type, userId, related));
  }

  @PostMapping("/aiDismiss")
  public ResultModel<Map<String, Object>> dismiss(
      @RequestParam long taskId,
      @RequestParam long messageId,
      @RequestParam String type,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long related) {
    return ResultModel.ok(ai.dismiss(taskId, messageId, type, userId, related));
  }
}
