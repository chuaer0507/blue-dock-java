package com.bluedock.task.web.dto;

import com.bluedock.task.domain.TaskFile;
import java.time.LocalDateTime;

public record TaskFileView(
    long id,
    long projectId,
    long taskId,
    String name,
    long size,
    String extension,
    String path,
    String thumbnail,
    long userId,
    int downloadCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TaskFileView from(TaskFile f) {
    return new TaskFileView(
        f.getId(),
        f.getProjectId(),
        f.getTaskId(),
        f.getName(),
        f.getSize(),
        f.getExtension(),
        f.getPath(),
        f.getThumbnail(),
        f.getUserId(),
        f.getDownloadCount(),
        f.getCreatedAt(),
        f.getUpdatedAt());
  }
}
