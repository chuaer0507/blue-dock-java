package com.bluedock.project.domain;

import java.time.LocalDateTime;

public class ProjectLog {
  private long id;
  private long projectId;
  private long columnId;
  private long taskId;
  private int taskOnly;
  private long userId;
  private String detail;
  private String recordJson;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getProjectId() {
    return projectId;
  }

  public void setProjectId(long projectId) {
    this.projectId = projectId;
  }

  public long getColumnId() {
    return columnId;
  }

  public void setColumnId(long columnId) {
    this.columnId = columnId;
  }

  public long getTaskId() {
    return taskId;
  }

  public void setTaskId(long taskId) {
    this.taskId = taskId;
  }

  public int getTaskOnly() {
    return taskOnly;
  }

  public void setTaskOnly(int taskOnly) {
    this.taskOnly = taskOnly;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getRecordJson() {
    return recordJson;
  }

  public void setRecordJson(String recordJson) {
    this.recordJson = recordJson;
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
