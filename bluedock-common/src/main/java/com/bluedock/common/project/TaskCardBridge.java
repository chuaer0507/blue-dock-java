package com.bluedock.common.project;

import java.util.Map;

/**
 * 会话发送任务卡片（{@code sendTaskId}）；由 bluedock-task 实现，messenger 可选注入。
 */
public interface TaskCardBridge {
  /**
   * 校验当前用户可见后返回卡片载荷（camelCase）。不可见 / 不存在时抛业务异常。
   *
   * @param note 可选留言，写入载荷 {@code note}
   */
  Map<String, Object> buildCard(long taskId, long userId, String note);

  /**
   * 若会话为任务群，将卡片任务与源任务建立关联（静默失败）。
   */
  void linkFromDialogIfTaskGroup(long dialogId, long messageId, long taskId, long userId);
}
