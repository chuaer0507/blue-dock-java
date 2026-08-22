package com.bluedock.auth.web.dto;

import com.bluedock.auth.domain.UserAccount;

/** 出站用户视图：禁止含 password。 */
public record UserPublicView(
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
    String lang) {

  public static UserPublicView from(UserAccount u) {
    return new UserPublicView(
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
        nullToEmpty(u.getLang()));
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
