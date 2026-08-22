package com.bluedock.common.report;

/** 工作报告分享到会话；由 messenger 实现，report 可选注入。 */
public interface ReportDialogBridge {
  /**
   * 以当前登录用户身份向会话发送文本消息。
   *
   * @return 消息 ID；失败或未接入时返回 0
   */
  long sendText(long dialogId, String text);
}
