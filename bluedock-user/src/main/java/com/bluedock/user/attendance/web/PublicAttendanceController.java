package com.bluedock.user.attendance.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.attendance.service.UserAttendanceService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/attendance")
public class PublicAttendanceController {
  private final UserAttendanceService attendances;

  public PublicAttendanceController(UserAttendanceService attendances) {
    this.attendances = attendances;
  }

  @GetMapping("/install")
  public ResultModel<Map<String, Object>> install() {
    return ResultModel.ok(attendances.installHint());
  }

  @PostMapping("/report")
  public ResultModel<Map<String, Object>> report(
      @RequestParam String macAddress, @RequestParam String key) {
    return ResultModel.ok(attendances.report(macAddress, key));
  }

  @GetMapping("/report")
  public ResultModel<Map<String, Object>> reportGet(
      @RequestParam String macAddress, @RequestParam String key) {
    return ResultModel.ok(attendances.report(macAddress, key));
  }

  @PostMapping("/face")
  public ResultModel<Map<String, Object>> face(
      @RequestParam long userId,
      @RequestParam long faceCaptureObjectId,
      @RequestParam String key) {
    return ResultModel.ok(attendances.reportFace(userId, faceCaptureObjectId, key));
  }
}
