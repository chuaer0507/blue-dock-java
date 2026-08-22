package com.bluedock.common.browse;

/** 浏览 / 最近访问写入；由 user 模块实现，task/file 可选注入。 */
public interface BrowseRecorder {
  void recordTask(long userId, long taskId);

  void recordFile(long userId, long fileId);

  void recordTaskFile(long userId, long taskFileId, long taskId);
}
