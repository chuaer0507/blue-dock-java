package com.bluedock.common.project;

/** 删列时级联软删列内任务；由 bluedock-task 实现，project 可选注入。 */
public interface TaskColumnCascadeBridge {
  void softDeleteByColumn(long projectId, long columnId, long userId);
}
