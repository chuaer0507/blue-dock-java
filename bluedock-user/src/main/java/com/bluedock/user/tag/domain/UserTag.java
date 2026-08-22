package com.bluedock.user.tag.domain;

import java.time.LocalDateTime;

/** 个性标签（贴在用户身上）。 */
public class UserTag {
  private long id;
  private long userId;
  private long creatorUserId;
  private String name;
  private LocalDateTime deletedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getCreatorUserId() {
    return creatorUserId;
  }

  public void setCreatorUserId(long creatorUserId) {
    this.creatorUserId = creatorUserId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }
}
