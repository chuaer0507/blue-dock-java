package com.bluedock.common.notify;

/** 系统消息机器人（{@code system-msg@bot.system}）私聊；由 messenger 实现。 */
public interface SystemMsgDmBridge {
  String BOT_EMAIL = "system-msg@bot.system";

  /**
   * 以系统消息机器人身份向用户单聊发送文本。
   *
   * @return 消息 ID；机器人未种子 / 失败时返回 0
   */
  long sendDm(long userId, String text);
}
