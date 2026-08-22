package com.bluedock.common.project;

/**
 * 会话消息中的任务 @# 提及 → 任务双向关联；由 bluedock-task 实现，messenger 可选注入。
 */
public interface TaskMentionBridge {

  /**
   * 解析文本/HTML 中的任务提及，对挂在该会话上的源任务建立双向关联。
   * 无任务群 / 无提及 / 无权限时静默跳过。
   */
  void recordMentionsFromMessage(long dialogId, long messageId, long userId, String msgBody);
}
