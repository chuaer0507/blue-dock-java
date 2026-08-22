package com.bluedock.common.project;

/** 未领取任务提醒 → 项目群 task-alert 机器人；由 messenger 实现。 */
public interface UnclaimedTaskRemindBridge {
  String BOT_EMAIL = "task-alert@bot.system";

  /**
   * 以任务提醒机器人向项目群发送文本。
   *
   * @return 消息 ID；失败返回 0
   */
  long sendToDialog(long dialogId, String text);
}
