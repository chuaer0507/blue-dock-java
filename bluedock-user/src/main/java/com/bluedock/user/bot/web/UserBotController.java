package com.bluedock.user.bot.web;

import com.bluedock.common.model.ResultModel;
import com.bluedock.user.bot.service.UserBotService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/userBot")
public class UserBotController {
  private final UserBotService bots;

  public UserBotController(UserBotService bots) {
    this.bots = bots;
  }

  @GetMapping("/list")
  public ResultModel<Map<String, Object>> list() {
    return ResultModel.ok(bots.list());
  }

  @GetMapping("/info")
  public ResultModel<Map<String, Object>> info(@RequestParam Long id) {
    return ResultModel.ok(bots.info(id));
  }

  @PostMapping("/edit")
  public ResultModel<Map<String, Object>> edit(@RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    Long id = b.get("id") instanceof Number n ? n.longValue() : null;
    Integer clearDay = b.get("clearDay") instanceof Number n ? n.intValue() : null;
    Object events = b.get("webhookEvents");
    String webhookUrl = str(b.get("webhookUrl"));
    return ResultModel.ok(
        bots.edit(id, str(b.get("name")), str(b.get("avatar")), clearDay, webhookUrl, events));
  }

  @GetMapping("/delete")
  public ResultModel<Void> delete(@RequestParam Long id, @RequestParam String remark) {
    bots.delete(id, remark);
    return ResultModel.ok();
  }

  private static String str(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
