package com.bluedock.boot.web;

import com.bluedock.common.model.ResultModel;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 进程健康探测；版本信息见 {@code /api/system/version}（bluedock-system）。 */
@RestController
public class HealthController {
  @GetMapping("/api/health")
  public ResultModel<Map<String, String>> health() {
    return ResultModel.ok(Map.of("status", "UP"));
  }
}
