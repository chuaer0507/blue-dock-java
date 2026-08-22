package com.bluedock.user.delete.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.delete.service.UserDeleteAccountService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/delete")
public class UserDeleteAccountController {
  private final UserDeleteAccountService deletes;

  public UserDeleteAccountController(UserDeleteAccountService deletes) {
    this.deletes = deletes;
  }

  /**
   * 注销账号。{@code type}=warning|confirm；confirm 时 {@code regVerify} 开则要 {@code code}，否则
   * RSA {@code password}+{@code keyId}。
   */
  @GetMapping("/account")
  public ResultModel<Map<String, Object>> account(
      @RequestParam(required = false) String type,
      @RequestParam String email,
      @RequestParam String reason,
      @RequestParam(required = false) String password,
      @RequestParam(required = false) String keyId,
      @RequestParam(required = false) String code) {
    return ResultModel.ok(deletes.handle(type, email, reason, password, keyId, code));
  }
}
