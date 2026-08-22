package com.bluedock.file.domain;

import java.time.LocalDateTime;

public class FileEntry {
  private long id;
  private long parentId;
  private String name;
  private String type;
  private String extension;
  private long size;
  private String hash;
  private String path;
  private long userId;
  private long createdUserId;
  private int isShared;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }
  public long getParentId() { return parentId; }
  public void setParentId(long parentId) { this.parentId = parentId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getExtension() { return extension; }
  public void setExtension(String extension) { this.extension = extension; }
  public long getSize() { return size; }
  public void setSize(long size) { this.size = size; }
  public String getHash() { return hash; }
  public void setHash(String hash) { this.hash = hash; }
  public String getPath() { return path; }
  public void setPath(String path) { this.path = path; }
  public long getUserId() { return userId; }
  public void setUserId(long userId) { this.userId = userId; }
  public long getCreatedUserId() { return createdUserId; }
  public void setCreatedUserId(long createdUserId) { this.createdUserId = createdUserId; }
  public int getIsShared() { return isShared; }
  public void setIsShared(int isShared) { this.isShared = isShared; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
  public LocalDateTime getDeletedAt() { return deletedAt; }
  public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
