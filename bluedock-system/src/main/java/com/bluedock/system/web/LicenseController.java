package com.bluedock.system.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.service.LicenseService;
import com.bluedock.system.service.OnlineLicenseService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LicenseController {
  private final LicenseService licenses;
  private final OnlineLicenseService online;

  public LicenseController(LicenseService licenses, OnlineLicenseService online) {
    this.licenses = licenses;
    this.online = online;
  }

  @GetMapping("/license/status")
  public ResultModel<Map<String, Object>> status() {
    return ResultModel.ok(licenses.status());
  }

  @PostMapping("/system/license")
  public ResultModel<Map<String, Object>> save(@RequestParam String license) {
    return ResultModel.ok(licenses.save(license));
  }

  @GetMapping("/license/email/send")
  public ResultModel<Map<String, Object>> emailSend(@RequestParam String email) {
    return ResultModel.ok(online.sendEmail(email));
  }

  @GetMapping("/license/login")
  public ResultModel<Map<String, Object>> login(
      @RequestParam String email, @RequestParam String code) {
    return ResultModel.ok(online.login(email, code));
  }

  @GetMapping("/license/login/confirm")
  public ResultModel<Map<String, Object>> confirm(@RequestParam String token) {
    return ResultModel.ok(online.confirm(token));
  }

  @GetMapping("/license/trial")
  public ResultModel<Map<String, Object>> trial(@RequestParam(required = false) String email) {
    return ResultModel.ok(online.trial(email));
  }

  @GetMapping("/license/refresh")
  public ResultModel<Map<String, Object>> refresh() {
    return ResultModel.ok(online.refresh());
  }

  @GetMapping("/license/logout")
  public ResultModel<Map<String, Object>> logout() {
    return ResultModel.ok(online.logout());
  }
}
