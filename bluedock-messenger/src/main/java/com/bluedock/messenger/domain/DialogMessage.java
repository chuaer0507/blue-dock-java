package com.bluedock.messenger.domain;

import java.time.LocalDateTime;

public class DialogMessage {
  private long id;
  private long dialogId;
  private long userId;
  private String type;
  private String body;
  private long replyId;
  private long tagUserId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getDialogId() {
    return dialogId;
  }

  public void setDialogId(long dialogId) {
    this.dialogId = dialogId;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public long getReplyId() {
    return replyId;
  }

  public void setReplyId(long replyId) {
    this.replyId = replyId;
  }

  public long getTagUserId() {
    return tagUserId;
  }

  public void setTagUserId(long tagUserId) {
    this.tagUserId = tagUserId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
