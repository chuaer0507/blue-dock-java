package com.bluedock.common.notify;

/** bluedock_settings.name 常量（邮件 / 推送 / 未读汇总游标）。 */
public final class NotifySettingNames {
  public static final String EMAIL = "emailSetting";
  public static final String APP_PUSH = "appPushSetting";
  /** 未读汇总上次处理水位：{@code {timeUser,timeGroup}}。 */
  public static final String EMAIL_LAST_NOTICE = "emailLastNotice";

  private NotifySettingNames() {}
}
