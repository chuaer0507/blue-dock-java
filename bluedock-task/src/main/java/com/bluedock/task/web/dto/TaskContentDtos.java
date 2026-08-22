package com.bluedock.task.web.dto;

import com.bluedock.task.domain.TaskContent;
import java.time.LocalDateTime;
import java.util.List;

public final class TaskContentDtos {
  private TaskContentDtos() {}

  public record TaskContentView(
      long id,
      long projectId,
      long taskId,
      long userId,
      String description,
      String content,
      String name,
      LocalDateTime createdAt) {

    public static TaskContentView from(TaskContent c, String taskName) {
      return new TaskContentView(
          c.getId(),
          c.getProjectId(),
          c.getTaskId(),
          c.getUserId(),
          c.getDescription() == null ? "" : c.getDescription(),
          c.getContent() == null ? "" : c.getContent(),
          taskName == null ? "" : taskName,
          c.getCreatedAt());
    }
  }

  public record TaskContentHistoryItem(
      long id, long taskId, long userId, String description, LocalDateTime createdAt) {

    public static TaskContentHistoryItem from(TaskContent c) {
      return new TaskContentHistoryItem(
          c.getId(),
          c.getTaskId(),
          c.getUserId(),
          c.getDescription() == null ? "" : c.getDescription(),
          c.getCreatedAt());
    }
  }

  public record TaskContentHistoryPage(List<TaskContentHistoryItem> items, PageMeta meta) {}

  public record PageMeta(int page, int pageSize, long totalSize, int totalPage) {}
}
