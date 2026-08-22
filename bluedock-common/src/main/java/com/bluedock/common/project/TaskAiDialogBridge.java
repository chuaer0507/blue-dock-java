package com.bluedock.common.project;

import java.util.Collection;
import java.util.Map;

/**
 * 任务 AI 建议 → 任务群 Markdown 卡片；由 messenger 实现，task 可选注入。
 */
public interface TaskAiDialogBridge {
  String AI_BOT_EMAIL = "ai-openai@bot.system";

  /**
   * 确保任务群存在，以 AI 机器人身份发送建议 Markdown。
   *
   * @return 消息 ID；机器人未种子 / 发送失败时返回 0
   */
  long publishSuggestion(
      long taskId,
      String taskName,
      long ownerUserId,
      Collection<Long> memberIds,
      String markdown);

  /**
   * 在消息中为 {@code :::ai-action} 追加 {@code status=}；返回更新后的消息视图（camelCase），失败返回 null。
   */
  Map<String, Object> updateActionStatus(
      long dialogId, long messageId, String type, String status, long userId, long related);
}
