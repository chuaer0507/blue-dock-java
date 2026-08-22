package com.bluedock.project.domain;

import java.time.LocalDateTime;

public class Project {
  private long id;
  private String name;
  private String description;
  private long userId;
  private int isPersonal;
  private long dialogId;
  private String archiveMethod = "system";
  private int archiveDays = 30;
  private String aiAutoAnalyze = "open";
  /** 1=open · 0=close */
  private int departmentOwnerView = 1;
  private String taskTemplateShare = "open";
  private LocalDateTime archivedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;
  private int myOwner;
  /** 当前用户对该项目的置顶时间（列表接口）；未置顶为 null。 */
  private LocalDateTime myTopAt;

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public int getIsPersonal() {
    return isPersonal;
  }

  public void setIsPersonal(int isPersonal) {
    this.isPersonal = isPersonal;
  }

  public long getDialogId() {
    return dialogId;
  }

  public void setDialogId(long dialogId) {
    this.dialogId = dialogId;
  }

  public String getArchiveMethod() {
    return archiveMethod;
  }

  public void setArchiveMethod(String archiveMethod) {
    this.archiveMethod = archiveMethod;
  }

  public int getArchiveDays() {
    return archiveDays;
  }

  public void setArchiveDays(int archiveDays) {
    this.archiveDays = archiveDays;
  }

  public String getAiAutoAnalyze() {
    return aiAutoAnalyze;
  }

  public void setAiAutoAnalyze(String aiAutoAnalyze) {
    this.aiAutoAnalyze = aiAutoAnalyze;
  }

  public int getDepartmentOwnerView() {
    return departmentOwnerView;
  }

  public void setDepartmentOwnerView(int departmentOwnerView) {
    this.departmentOwnerView = departmentOwnerView;
  }

  public String getTaskTemplateShare() {
    return taskTemplateShare;
  }

  public void setTaskTemplateShare(String taskTemplateShare) {
    this.taskTemplateShare = taskTemplateShare;
  }

  public LocalDateTime getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(LocalDateTime archivedAt) {
    this.archivedAt = archivedAt;
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

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public int getMyOwner() {
    return myOwner;
  }

  public void setMyOwner(int myOwner) {
    this.myOwner = myOwner;
  }

  public LocalDateTime getMyTopAt() {
    return myTopAt;
  }

  public void setMyTopAt(LocalDateTime myTopAt) {
    this.myTopAt = myTopAt;
  }
}
