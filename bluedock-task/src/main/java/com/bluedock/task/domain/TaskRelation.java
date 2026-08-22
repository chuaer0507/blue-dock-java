package com.bluedock.task.domain;

import java.time.LocalDateTime;

public class TaskRelation {
  public static final String DIRECTION_MENTION = "mention";
  public static final String DIRECTION_MENTIONED_BY = "mentioned_by";

  private long id;
  private long taskId;
  private long relatedTaskId;
  private String direction;
  private long dialogId;
  private long messageId;
  private long userId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getTaskId() {
    return taskId;
  }

  public void setTaskId(long taskId) {
    this.taskId = taskId;
  }

  public long getRelatedTaskId() {
    return relatedTaskId;
  }

  public void setRelatedTaskId(long relatedTaskId) {
    this.relatedTaskId = relatedTaskId;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public long getDialogId() {
    return dialogId;
  }

  public void setDialogId(long dialogId) {
    this.dialogId = dialogId;
  }

  public long getMessageId() {
    return messageId;
  }

  public void setMessageId(long messageId) {
    this.messageId = messageId;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
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
