package com.bluedock.common.attendance;

import java.time.LocalDate;

/**
 * 签到提醒请假 / 外出过滤；由 approve 插件实现。
 *
 * <p>无 Bean 时提醒任务视为无人请假（全部可推）。插件安装后提供实现即可生效。
 */
public interface AttendanceLeaveBridge {

  /**
   * 用户在指定自然日是否处于已通过的请假或外出审批中。
   *
   * @return true 表示当日不应发送签到提醒
   */
  boolean isAwayOn(long userId, LocalDate day);
}
