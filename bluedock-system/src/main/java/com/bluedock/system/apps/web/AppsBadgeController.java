package com.bluedock.system.apps.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.apps.service.AppBadgeService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apps/badge")
public class AppsBadgeController {
  private final AppBadgeService badges;

  public AppsBadgeController(AppBadgeService badges) {
    this.badges = badges;
  }

  @PostMapping("/set")
  public ResultModel<Map<String, Object>> set(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(
        badges.set(
            str(b.get("appId")),
            str(b.get("secret")),
            b.get("userId"),
            str(b.get("menuKey")),
            b.get("count"),
            b.get("dot")));
  }

  @PostMapping("/clear")
  public ResultModel<Map<String, Object>> clear(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(badges.clear(str(b.get("appId")), str(b.get("menuKey"))));
  }

  @GetMapping("/list")
  public ResultModel<Map<String, Map<String, Map<String, Object>>>> list() {
    return ResultModel.ok(badges.list());
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
