package com.bluedock.user.device.web;

import com.bluedock.auth.security.BearerTokens;
import com.bluedock.common.model.ResultModel;
import com.bluedock.user.device.service.UserDeviceService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/device")
public class UserDeviceController {
  private final UserDeviceService devices;

  public UserDeviceController(UserDeviceService devices) {
    this.devices = devices;
  }

  @GetMapping("/list")
  public ResultModel<Map<String, Object>> list(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    return ResultModel.ok(devices.list(BearerTokens.extract(authorization)));
  }

  @GetMapping("/logout")
  public ResultModel<Void> logout(@RequestParam Long id) {
    devices.logout(id);
    return ResultModel.ok();
  }

  @GetMapping("/edit")
  public ResultModel<Void> edit(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = false) String deviceName,
      @RequestParam(required = false) String appBrand,
      @RequestParam(required = false) String appModel,
      @RequestParam(required = false) String appOs) {
    Map<String, Object> patch = new LinkedHashMap<>();
    put(patch, "deviceName", deviceName);
    put(patch, "appBrand", appBrand);
    put(patch, "appModel", appModel);
    put(patch, "appOs", appOs);
    devices.edit(BearerTokens.extract(authorization), patch);
    return ResultModel.ok();
  }

  private static void put(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
