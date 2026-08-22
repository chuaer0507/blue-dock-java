package com.bluedock.system.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.service.AttendanceExportService;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/attendance")
public class AttendanceExportController {
  private final AttendanceExportService exports;

  public AttendanceExportController(AttendanceExportService exports) {
    this.exports = exports;
  }

  @GetMapping("/export")
  public ResultModel<Map<String, Object>> export(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String userIds,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String time) {
    String users = userId != null && !userId.isBlank() ? userId : userIds;
    return ResultModel.ok(exports.export(users, date, time));
  }

  @GetMapping("/download")
  public ResponseEntity<InputStreamResource> download(@RequestParam String key) throws IOException {
    return exports.download(key);
  }
}
