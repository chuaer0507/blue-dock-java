package com.bluedock.org.department.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Department {
  private long id;
  private String name;
  private long parentId;
  private long ownerUserId;
  private long dialogId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<Long> deputyUserIds = new ArrayList<>();

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }

  public long getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(long ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public long getDialogId() {
    return dialogId;
  }

  public void setDialogId(long dialogId) {
    this.dialogId = dialogId;
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

  public List<Long> getDeputyUserIds() {
    return deputyUserIds;
  }

  public void setDeputyUserIds(List<Long> deputyUserIds) {
    this.deputyUserIds = deputyUserIds == null ? new ArrayList<>() : deputyUserIds;
  }
}
