package com.bluedock.task.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class ProjectUserTaskDtos {
  private ProjectUserTaskDtos() {}

  public record PageMeta(int page, int pageSize, long totalSize, int totalPage) {}

  public record UserTaskView(
      long id,
      long parentId,
      long projectId,
      String projectName,
      long columnId,
      String name,
      String color,
      String description,
      LocalDateTime startAt,
      LocalDateTime endAt,
      LocalDateTime completeAt,
      int visibility,
      int owner,
      boolean today,
      boolean overdue,
      boolean departmentReadonly,
      LocalDateTime createdAt) {}

  public record UserTaskPage(List<UserTaskView> items, PageMeta meta) {}

  public record UserTaskCounts(long project, long todo, long done) {}

  public record UserProjectPage(
      List<com.bluedock.project.web.dto.ProjectView> items, PageMeta meta) {}
}
