package com.bluedock.user.web.dto;

import com.bluedock.auth.domain.UserAccount;

/** 会员搜索基础字段（无 password / 扩展资料）。 */
public record UserSearchView(
    long userId,
    String email,
    String nickname,
    String profession,
    String userImage,
    String nameAz) {

  public static UserSearchView from(UserAccount u) {
    return new UserSearchView(
        u.getUserId(),
        u.getEmail() == null ? "" : u.getEmail(),
        nullToEmpty(u.getNickname()),
        nullToEmpty(u.getProfession()),
        nullToEmpty(u.getUserImage()),
        nullToEmpty(u.getNameAz()));
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
