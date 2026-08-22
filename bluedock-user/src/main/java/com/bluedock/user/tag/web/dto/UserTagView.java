package com.bluedock.user.tag.web.dto;

import com.bluedock.user.tag.domain.UserTag;

/** 个性标签出站视图。 */
public record UserTagView(
    long id,
    long userId,
    long creatorUserId,
    String name,
    long recognizeCount,
    boolean recognized) {

  public static UserTagView from(UserTag t, long recognizeCount, boolean recognized) {
    return new UserTagView(
        t.getId(),
        t.getUserId(),
        t.getCreatorUserId(),
        t.getName() == null ? "" : t.getName(),
        recognizeCount,
        recognized);
  }
}
