package com.bluedock.user.web;

import com.bluedock.auth.web.dto.UserPublicView;
import com.bluedock.common.model.ResultModel;
import com.bluedock.user.service.UserProfileService;
import com.bluedock.user.web.dto.UserExtraView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {
  private final UserProfileService profiles;

  public UserProfileController(UserProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/basic")
  public ResultModel<UserPublicView> basic(@RequestParam long userId) {
    return ResultModel.ok(profiles.basic(userId));
  }

  /** 会员扩展信息；{@code userId} 缺省为当前用户。 */
  @GetMapping("/extra")
  public ResultModel<UserExtraView> extra(@RequestParam(required = false) Long userId) {
    return ResultModel.ok(profiles.extra(userId));
  }

  /** 历史契约：GET 传参修改资料。 */
  @GetMapping("/editData")
  public ResultModel<UserPublicView> editData(
      @RequestParam(required = false) String nickname,
      @RequestParam(required = false) String userImage,
      @RequestParam(required = false) String profession,
      @RequestParam(required = false) String telephone,
      @RequestParam(required = false) String birthday,
      @RequestParam(required = false) String address,
      @RequestParam(required = false) String introduction,
      @RequestParam(required = false) String lang) {
    return ResultModel.ok(
        profiles.editData(nickname, userImage, profession, telephone, birthday, address, introduction, lang));
  }

  /** 修改自己的密码：RSA {@code oldPassword}/{@code password}+{@code keyId}。 */
  @GetMapping("/editPassword")
  public ResultModel<UserPublicView> editPassword(
      @RequestParam String oldPassword,
      @RequestParam String password,
      @RequestParam String keyId) {
    return ResultModel.ok(profiles.editPassword(oldPassword, password, keyId));
  }
}
