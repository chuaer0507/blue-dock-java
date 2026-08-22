package com.bluedock.common.project;

import java.time.LocalDateTime;

/** 项目归档/取消归档时级联任务；由 bluedock-task 实现，project 可选注入。 */
public interface TaskProjectArchiveBridge {
  /** 归档项目下未归档任务，并标记 {@code archived_follow=1}。 */
  void archiveByProject(long projectId, long userId, LocalDateTime archivedAt);

  /** 恢复因项目归档而跟随归档的任务（{@code archived_follow=1}）。 */
  void unarchiveByProject(long projectId, long userId);
}
