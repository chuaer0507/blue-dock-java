package com.bluedock.task.domain;

import java.time.LocalDateTime;

public class TaskAiEvent {
  public static final String TYPE_DESCRIPTION = "description";
  public static final String TYPE_SUBTASKS = "subtasks";
  public static final String TYPE_ASSIGNEE = "assignee";
  public static final String TYPE_SIMILAR = "similar";

  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_PROCESSING = "processing";
  public static final String STATUS_COMPLETED = "completed";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_SKIPPED = "skipped";
  public static final String STATUS_APPLIED = "applied";
  public static final String STATUS_DISMISSED = "dismissed";

  public static final int MAX_RETRY = 3;

  private long id;
  private long taskId;
  private String eventType;
  private String status;
  private int retryCount;
  private String resultJson;
  private String error;
  private long messageId;
  private LocalDateTime executedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static String[] eventTypes() {
    return new String[] {TYPE_DESCRIPTION, TYPE_SUBTASKS, TYPE_ASSIGNEE, TYPE_SIMILAR};
  }

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

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public long getMessageId() {
    return messageId;
  }

  public void setMessageId(long messageId) {
    this.messageId = messageId;
  }

  public LocalDateTime getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(LocalDateTime executedAt) {
    this.executedAt = executedAt;
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
