package com.bluedock.common.attendance;

/** 签到提醒 → 签到机器人私聊；由 messenger 实现。 */
public interface AttendanceRemindBridge {
  String BOT_EMAIL = "attendance@bot.system";

  /**
   * 以签到机器人身份向用户单聊发送文本。
   *
   * @return 消息 ID；机器人未种子 / 失败时返回 0
   */
  long sendDm(long userId, String text);
}
