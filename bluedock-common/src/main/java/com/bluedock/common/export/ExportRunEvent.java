package com.bluedock.common.export;

import java.util.List;

/** Kafka {@code bluedock.export.run} 载荷。 */
public record ExportRunEvent(
    String eventId,
    String kind,
    long requesterUserId,
    List<Long> userIds,
    String timeStart,
    String timeEnd,
    String timeType,
    String processName,
    String status) {

  public static final String KIND_TASK_STATS = "task_stats";
  public static final String KIND_TASK_OVERDUE = "task_overdue";
  /** 签到导出：{@code timeStart/timeEnd}=日期；{@code timeType}=班次 {@code HH:mm,HH:mm}。 */
  public static final String KIND_ATTENDANCE = "attendance";
  /** 审批导出：{@code processName} 必填；{@code status} 可选；日期在 timeStart/timeEnd。 */
  public static final String KIND_APPROVE = "approve";
  public static final String TIME_TASK = "taskTime";
  public static final String TIME_CREATED = "createdTime";

  /** 任务 / 签到等既有调用兼容构造。 */
  public ExportRunEvent(
      String eventId,
      String kind,
      long requesterUserId,
      List<Long> userIds,
      String timeStart,
      String timeEnd,
      String timeType) {
    this(eventId, kind, requesterUserId, userIds, timeStart, timeEnd, timeType, null, null);
  }
}
