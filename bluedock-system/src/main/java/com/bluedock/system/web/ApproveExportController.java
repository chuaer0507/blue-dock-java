package com.bluedock.system.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.service.ApproveExportService;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approve")
public class ApproveExportController {
  private final ApproveExportService exports;

  public ApproveExportController(ApproveExportService exports) {
    this.exports = exports;
  }

  @GetMapping("/export")
  public ResultModel<Map<String, Object>> export(
      @RequestParam String processName,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String date) {
    return ResultModel.ok(exports.export(processName, status, date));
  }

  @GetMapping("/download")
  public ResponseEntity<InputStreamResource> download(@RequestParam String key) throws IOException {
    return exports.download(key);
  }
}
