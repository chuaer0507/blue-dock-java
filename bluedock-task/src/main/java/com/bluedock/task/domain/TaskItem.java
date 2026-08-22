package com.bluedock.task.domain;

import java.time.LocalDateTime;

public class TaskItem {
  private long id;
  private long parentId;
  private long projectId;
  private long columnId;
  private long dialogId;
  private String name;
  private String color;
  private String description;
  private LocalDateTime startAt;
  private LocalDateTime endAt;
  private LocalDateTime completeAt;
  private int visibility;
  private int priorityLevel;
  private String priorityName;
  private String priorityColor;
  private long flowItemId;
  private String flowItemName;
  private int sort;
  /** 0=关 · 1=天 · 2=周 · 3=月 · 4=年。 */
  private int loop;
  /** 下一周期计划截止（通常等于 end_at）。 */
  private LocalDateTime loopAt;
  private long userId;
  /** 主负责人 userId；无负责人时为 0（查询填充，非表列）。 */
  private long ownerUserId;
  private LocalDateTime archivedAt;
  private LocalDateTime deletedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
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

  public long getDialogId() {
    return dialogId;
  }

  public void setDialogId(long dialogId) {
    this.dialogId = dialogId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime getStartAt() {
    return startAt;
  }

  public void setStartAt(LocalDateTime startAt) {
    this.startAt = startAt;
  }

  public LocalDateTime getEndAt() {
    return endAt;
  }

  public void setEndAt(LocalDateTime endAt) {
    this.endAt = endAt;
  }

  public LocalDateTime getCompleteAt() {
    return completeAt;
  }

  public void setCompleteAt(LocalDateTime completeAt) {
    this.completeAt = completeAt;
  }

  public int getVisibility() {
    return visibility;
  }

  public void setVisibility(int visibility) {
    this.visibility = visibility;
  }

  public int getPriorityLevel() {
    return priorityLevel;
  }

  public void setPriorityLevel(int priorityLevel) {
    this.priorityLevel = priorityLevel;
  }

  public String getPriorityName() {
    return priorityName;
  }

  public void setPriorityName(String priorityName) {
    this.priorityName = priorityName;
  }

  public String getPriorityColor() {
    return priorityColor;
  }

  public void setPriorityColor(String priorityColor) {
    this.priorityColor = priorityColor;
  }

  public long getFlowItemId() {
    return flowItemId;
  }

  public void setFlowItemId(long flowItemId) {
    this.flowItemId = flowItemId;
  }

  public String getFlowItemName() {
    return flowItemName;
  }

  public void setFlowItemName(String flowItemName) {
    this.flowItemName = flowItemName;
  }

  public int getSort() {
    return sort;
  }

  public void setSort(int sort) {
    this.sort = sort;
  }

  public int getLoop() {
    return loop;
  }

  public void setLoop(int loop) {
    this.loop = loop;
  }

  public LocalDateTime getLoopAt() {
    return loopAt;
  }

  public void setLoopAt(LocalDateTime loopAt) {
    this.loopAt = loopAt;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(long ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public LocalDateTime getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(LocalDateTime archivedAt) {
    this.archivedAt = archivedAt;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
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
