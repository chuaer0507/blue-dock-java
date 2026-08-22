package com.bluedock.task.archive;

import com.bluedock.common.project.TaskProjectArchiveBridge;
import com.bluedock.task.repo.TaskRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class TaskProjectArchiveBridgeImpl implements TaskProjectArchiveBridge {
  private final TaskRepository tasks;

  public TaskProjectArchiveBridgeImpl(TaskRepository tasks) {
    this.tasks = tasks;
  }

  @Override
  public void archiveByProject(long projectId, long userId, LocalDateTime archivedAt) {
    tasks.archiveByProject(projectId, userId, archivedAt);
  }

  @Override
  public void unarchiveByProject(long projectId, long userId) {
    tasks.unarchiveFollowedByProject(projectId, userId);
  }
}
