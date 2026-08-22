package com.bluedock.user.apppush.web;

import com.bluedock.auth.security.BearerTokens;
import com.bluedock.common.model.ResultModel;
import com.bluedock.user.apppush.service.AppPushAliasService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/appPush")
public class AppPushAliasController {
  private final AppPushAliasService appPush;

  public AppPushAliasController(AppPushAliasService appPush) {
    this.appPush = appPush;
  }

  @GetMapping("/alias")
  public ResultModel<Map<String, Object>> alias(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "platform", required = false) String platform,
      @RequestParam Map<String, String> params) {
    return ResultModel.ok(
        appPush.handle(Map.copyOf(params), BearerTokens.extract(authorization), platform));
  }
}
