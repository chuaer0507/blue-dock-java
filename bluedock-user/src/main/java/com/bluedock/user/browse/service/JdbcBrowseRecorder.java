package com.bluedock.user.browse.service;

import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.user.browse.repo.RecentItemRepository;
import com.bluedock.user.browse.repo.TaskBrowseRepository;
import org.springframework.stereotype.Component;

@Component
public class JdbcBrowseRecorder implements BrowseRecorder {
  public static final String TYPE_TASK = "task";
  public static final String TYPE_FILE = "file";
  public static final String TYPE_TASK_FILE = "task_file";
  public static final String SOURCE_PROJECT = "project";
  public static final String SOURCE_FILESYSTEM = "filesystem";
  public static final String SOURCE_PROJECT_TASK = "project_task";

  private final TaskBrowseRepository taskBrowses;
  private final RecentItemRepository recentItems;

  public JdbcBrowseRecorder(TaskBrowseRepository taskBrowses, RecentItemRepository recentItems) {
    this.taskBrowses = taskBrowses;
    this.recentItems = recentItems;
  }

  @Override
  public void recordTask(long userId, long taskId) {
    taskBrowses.upsert(userId, taskId);
    recentItems.upsert(userId, TYPE_TASK, taskId, SOURCE_PROJECT, 0L);
  }

  @Override
  public void recordFile(long userId, long fileId) {
    recentItems.upsert(userId, TYPE_FILE, fileId, SOURCE_FILESYSTEM, 0L);
  }

  @Override
  public void recordTaskFile(long userId, long taskFileId, long taskId) {
    recentItems.upsert(userId, TYPE_TASK_FILE, taskFileId, SOURCE_PROJECT_TASK, taskId);
  }
}
