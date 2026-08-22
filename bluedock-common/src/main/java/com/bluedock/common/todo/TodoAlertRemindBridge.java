package com.bluedock.common.todo;

/** 待办到期提醒 → todo-alert 机器人私聊；由 messenger 实现。 */
public interface TodoAlertRemindBridge {
  String BOT_EMAIL = "todo-alert@bot.system";

  /**
   * 以待办提醒机器人身份向用户单聊发送文本。
   *
   * @return 消息 ID；机器人未种子 / 失败时返回 0
   */
  long sendDm(long userId, String text);
}
