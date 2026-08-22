package com.bluedock.common.project;

/**
 * 任务群消息可见性：messenger 在读写任务群时校验挂接任务对当前用户是否可见。
 * 由 bluedock-task 实现，messenger 可选注入。
 */
public interface TaskDialogAccessBridge {
  /**
   * @param taskId 任务群 {@code link_id}
   * @return 任务存在且当前用户可见（含项目成员前提由调用方保证时可再收紧）时 true
   */
  boolean canAccessTaskDialog(long taskId, long userId);
}
