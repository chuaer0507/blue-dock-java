package com.bluedock.task.web.dto;

import com.bluedock.task.domain.TaskTemplate;
import java.time.LocalDateTime;

public record TaskTemplateView(
    long id,
    long projectId,
    String projectName,
    String name,
    String title,
    String content,
    int sort,
    int isDefault,
    long userId,
    String userName,
    int useCount,
    LocalDateTime lastUsedAt,
    LocalDateTime createdAt) {

  public static TaskTemplateView from(TaskTemplate t) {
    return new TaskTemplateView(
        t.getId(),
        t.getProjectId(),
        t.getProjectName() == null ? "" : t.getProjectName(),
        t.getName() == null ? "" : t.getName(),
        t.getTitle() == null ? "" : t.getTitle(),
        t.getContent() == null ? "" : t.getContent(),
        t.getSort(),
        t.getIsDefault(),
        t.getUserId(),
        t.getUserName() == null ? "" : t.getUserName(),
        t.getUseCount(),
        t.getLastUsedAt(),
        t.getCreatedAt());
  }
}
