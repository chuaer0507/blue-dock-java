package com.bluedock.user.web;

import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.model.ResultModel;
import com.bluedock.user.service.UserAdminService;
import com.bluedock.user.web.dto.UserAdminView;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserAdminController {
  private final UserAdminService admins;

  public UserAdminController(UserAdminService admins) {
    this.admins = admins;
  }

  @GetMapping("/lists")
  public ResultModel<Map<String, Object>> lists(
      @RequestParam(required = false) String key,
      @RequestParam(required = false) String keys,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) Integer isBot) {
    String kw = key != null && !key.isBlank() ? key : keys;
    return ResultModel.ok(admins.lists(kw, page, pageSize, isBot));
  }

  @PostMapping("/createUser")
  public ResultModel<UserPublicView> createUser(
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String nickname,
      @RequestParam(required = false) String password,
      @RequestParam(required = false) String keyId,
      @RequestParam(required = false) String profession,
      @RequestParam(required = false) String identity,
      @RequestBody(required = false) Map<String, Object> body) {
    Map<String, Object> b = body == null ? Map.of() : body;
    String e = first(email, b.get("email"));
    String n = first(nickname, b.get("nickname"));
    String p = first(password, b.get("password"));
    String k = first(keyId, b.get("keyId"));
    String prof = first(profession, b.get("profession"));
    String id = first(identity, b.get("identity"));
    return ResultModel.ok(admins.createUser(e, n, p, k, prof, id));
  }

  /** 管理员操作用户：{@code type}=setAdmin|clearAdmin|setTemporary|clearTemporary|disable|enable；{@code disable} 须 {@code handoverUserId}。 */
  @GetMapping("/operation")
  public ResultModel<UserAdminView> operation(
      @RequestParam String type,
      @RequestParam long userId,
      @RequestParam(required = false) Long handoverUserId) {
    return ResultModel.ok(admins.operation(type, userId, handoverUserId));
  }

  private static String first(String query, Object bodyVal) {
    if (query != null && !query.isBlank()) {
      return query;
    }
    return bodyVal == null ? null : String.valueOf(bodyVal);
  }
}
