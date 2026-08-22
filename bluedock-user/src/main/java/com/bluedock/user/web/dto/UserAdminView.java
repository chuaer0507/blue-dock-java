package com.bluedock.user.web.dto;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.web.dto.UserPublicView;
import java.time.LocalDateTime;

/** 管理员用户列表项（无 password）。 */
public record UserAdminView(
    long userId,
    String email,
    String nickname,
    String userImage,
    String identity,
    String profession,
    String telephone,
    String birthday,
    String address,
    String introduction,
    String lang,
    int isBot,
    LocalDateTime disableAt) {

  public static UserAdminView from(UserAccount u) {
    return new UserAdminView(
        u.getUserId(),
        u.getEmail() == null ? "" : u.getEmail(),
        nullToEmpty(u.getNickname()),
        nullToEmpty(u.getUserImage()),
        nullToEmpty(u.getIdentity()),
        nullToEmpty(u.getProfession()),
        nullToEmpty(u.getTelephone()),
        nullToEmpty(u.getBirthday()),
        nullToEmpty(u.getAddress()),
        nullToEmpty(u.getIntroduction()),
        nullToEmpty(u.getLang()),
        u.getIsBot(),
        u.getDisableAt());
  }

  public UserPublicView toPublic() {
    return new UserPublicView(
        userId, email, nickname, userImage, identity, profession, telephone, birthday, address, introduction, lang);
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
