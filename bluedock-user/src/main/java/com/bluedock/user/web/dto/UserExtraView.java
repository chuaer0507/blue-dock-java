package com.bluedock.user.web.dto;

import com.bluedock.auth.domain.UserAccount;

/**
 * 会员扩展信息（相对搜索薄视图）；禁止含 password。
 */
public record UserExtraView(
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
    String nameAz,
    int emailVerify,
    int isBot) {

  public static UserExtraView from(UserAccount u) {
    return new UserExtraView(
        u.getUserId(),
        u.getEmail(),
        nullToEmpty(u.getNickname()),
        nullToEmpty(u.getUserImage()),
        nullToEmpty(u.getIdentity()),
        nullToEmpty(u.getProfession()),
        nullToEmpty(u.getTelephone()),
        nullToEmpty(u.getBirthday()),
        nullToEmpty(u.getAddress()),
        nullToEmpty(u.getIntroduction()),
        nullToEmpty(u.getLang()),
        nullToEmpty(u.getNameAz()),
        u.getEmailVerify(),
        u.getIsBot());
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
