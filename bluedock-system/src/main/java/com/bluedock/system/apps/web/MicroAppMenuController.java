package com.bluedock.system.apps.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.system.apps.service.MicroAppMenuService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class MicroAppMenuController {
  private final MicroAppMenuService menus;

  public MicroAppMenuController(MicroAppMenuService menus) {
    this.menus = menus;
  }

  @PostMapping("/microAppMenu")
  public ResultModel<List<Map<String, Object>>> microappMenu(
      @RequestParam(required = false) String type,
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    String t = type != null ? type : str(b.get("type"));
    if ("save".equalsIgnoreCase(t == null ? "" : t.trim())) {
      return ResultModel.ok(menus.save(b.get("list")));
    }
    return ResultModel.ok(menus.get());
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
