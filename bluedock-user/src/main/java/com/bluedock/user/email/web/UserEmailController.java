package com.bluedock.user.email.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.email.service.UserEmailService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/email")
public class UserEmailController {
  private final UserEmailService emails;

  public UserEmailController(UserEmailService emails) {
    this.emails = emails;
  }

  @GetMapping("/send")
  public ResultModel<Map<String, Object>> send() {
    return ResultModel.ok(emails.send());
  }

  @GetMapping("/edit")
  public ResultModel<Map<String, Object>> edit(@RequestParam String email) {
    return ResultModel.ok(emails.edit(email));
  }

  @GetMapping("/verification")
  public ResultModel<Map<String, Object>> verification(@RequestParam String code) {
    return ResultModel.ok(emails.verify(code));
  }
}
