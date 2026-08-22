package com.bluedock.project.web.dto;

import com.bluedock.project.domain.Project;
import java.time.LocalDateTime;

public record ProjectView(
    long id,
    String name,
    String description,
    long userId,
    int isPersonal,
    long dialogId,
    String archiveMethod,
    int archiveDays,
    String aiAutoAnalyze,
    String departmentOwnerView,
    String taskTemplateShare,
    int myOwner,
    LocalDateTime topAt,
    LocalDateTime archivedAt,
    LocalDateTime createdAt,
    Boolean departmentReadonly) {

  public static ProjectView from(Project p) {
    return from(p, null);
  }

  public static ProjectView from(Project p, Boolean departmentReadonly) {
    return new ProjectView(
        p.getId(),
        p.getName(),
        p.getDescription() == null ? "" : p.getDescription(),
        p.getUserId(),
        p.getIsPersonal(),
        p.getDialogId(),
        p.getArchiveMethod() == null ? "system" : p.getArchiveMethod(),
        p.getArchiveDays(),
        p.getAiAutoAnalyze() == null ? "open" : p.getAiAutoAnalyze(),
        p.getDepartmentOwnerView() == 0 ? "close" : "open",
        p.getTaskTemplateShare() == null ? "open" : p.getTaskTemplateShare(),
        p.getMyOwner(),
        p.getMyTopAt(),
        p.getArchivedAt(),
        p.getCreatedAt(),
        departmentReadonly);
  }
}
