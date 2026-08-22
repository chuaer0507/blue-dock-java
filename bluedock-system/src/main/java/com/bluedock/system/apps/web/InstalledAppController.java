package com.bluedock.system.apps.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.apps.service.InstalledAppService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用市场注册表（无 Docker 编排）。契约挂在 system 下供管理端联调。
 */
@RestController
@RequestMapping("/api/system/apps")
public class InstalledAppController {
  private final InstalledAppService apps;

  public InstalledAppController(InstalledAppService apps) {
    this.apps = apps;
  }

  @GetMapping("/catalog")
  public ResultModel<List<Map<String, Object>>> catalog() {
    return ResultModel.ok(apps.catalog());
  }

  @GetMapping("/installed")
  public ResultModel<List<Map<String, Object>>> installed() {
    return ResultModel.ok(apps.listForAdmin());
  }

  @PostMapping("/install")
  public ResultModel<Map<String, Object>> install(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(
        apps.install(
            str(b.get("id")),
            str(b.get("name")),
            str(b.get("secret")),
            str(b.get("version")),
            menus(b.get("menus"))));
  }

  @PostMapping("/update")
  public ResultModel<Map<String, Object>> update(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(
        apps.update(
            str(b.get("id")),
            str(b.get("name")),
            str(b.get("secret")),
            str(b.get("version")),
            menus(b.get("menus"))));
  }

  @PostMapping("/uninstall")
  public ResultModel<Map<String, Object>> uninstall(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    return ResultModel.ok(apps.uninstall(str(b.get("id"))));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> menus(Object raw) {
    return raw instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
