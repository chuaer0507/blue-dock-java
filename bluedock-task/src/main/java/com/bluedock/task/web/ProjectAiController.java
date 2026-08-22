package com.bluedock.task.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.task.service.TaskAiService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 废弃：{@code /api/project/ai/generate} 路由占位。 */
@RestController
@RequestMapping("/api/project/ai")
public class ProjectAiController {
  private final TaskAiService ai;

  public ProjectAiController(TaskAiService ai) {
    this.ai = ai;
  }

  @GetMapping("/generate")
  public ResultModel<Map<String, Object>> generateGet() {
    return ResultModel.ok(ai.projectGenerateDeprecated());
  }

  @PostMapping("/generate")
  public ResultModel<Map<String, Object>> generatePost() {
    return ResultModel.ok(ai.projectGenerateDeprecated());
  }
}
