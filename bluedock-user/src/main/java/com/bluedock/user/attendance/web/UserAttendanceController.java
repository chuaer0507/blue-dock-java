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
@RequestMapping("/api/users/attendance")
public class UserAttendanceController {
  private final UserAttendanceService attendances;

  public UserAttendanceController(UserAttendanceService attendances) {
    this.attendances = attendances;
  }

  @GetMapping("/get")
  public ResultModel<Map<String, Object>> get() {
    return ResultModel.ok(attendances.get());
  }

  @PostMapping("/save")
  public ResultModel<Map<String, Object>> save(
      @RequestParam(required = false) String macAddresses,
      @RequestParam(required = false) Integer punch,
      @RequestParam(required = false) Double latitude,
      @RequestParam(required = false) Double longitude,
      @RequestParam(required = false) Long faceUploadObjectId,
      @RequestParam(required = false) Long faceCaptureObjectId) {
    return ResultModel.ok(
        attendances.save(
            macAddresses, punch, latitude, longitude, faceUploadObjectId, faceCaptureObjectId));
  }

  @GetMapping("/list")
  public ResultModel<Map<String, Object>> list(
      @RequestParam(required = false) String yearMonth) {
    return ResultModel.ok(attendances.list(yearMonth));
  }
}