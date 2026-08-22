package com.bluedock.task.service;

import com.bluedock.task.domain.TaskItem;
import java.time.LocalDateTime;

/** 循环任务周期计算与下一份骨架。 */
final class TaskRecurring {
  /** 关闭。 */
  static final int OFF = 0;
  /** 每天。 */
  static final int DAY = 1;
  /** 每周。 */
  static final int WEEK = 2;
  /** 每月。 */
  static final int MONTH = 3;
  /** 每年。 */
  static final int YEAR = 4;

  private TaskRecurring() {}

  static boolean isValid(int loop) {
    return loop >= OFF && loop <= YEAR;
  }

  static LocalDateTime shift(LocalDateTime at, int loop) {
    if (at == null) {
      return null;
    }
    return switch (loop) {
      case DAY -> at.plusDays(1);
      case WEEK -> at.plusWeeks(1);
      case MONTH -> at.plusMonths(1);
      case YEAR -> at.plusYears(1);
      default -> at;
    };
  }

  /**
   * 根据已完成主任务生成下一周期主任务（未完成；不含负责人/标签/附件）。
   *
   * @param actorUserId 写入创建人
   */
  static TaskItem buildNext(
      TaskItem completed, long newId, long actorUserId, LocalDateTime now, int sort) {
    TaskItem next = new TaskItem();
    next.setId(newId);
    next.setParentId(0L);
    next.setProjectId(completed.getProjectId());
    next.setColumnId(completed.getColumnId());
    next.setDialogId(0L);
    next.setName(completed.getName());
    next.setDescription(completed.getDescription() == null ? "" : completed.getDescription());
    next.setColor(completed.getColor() == null ? "" : completed.getColor());
    next.setVisibility(completed.getVisibility());
    next.setPriorityLevel(completed.getPriorityLevel());
    next.setPriorityName(completed.getPriorityName() == null ? "" : completed.getPriorityName());
    next.setPriorityColor(completed.getPriorityColor() == null ? "" : completed.getPriorityColor());
    next.setFlowItemId(0L);
    next.setFlowItemName("");
    next.setSort(sort);
    next.setLoop(completed.getLoop());
    next.setStartAt(shift(completed.getStartAt(), completed.getLoop()));
    next.setEndAt(shift(completed.getEndAt(), completed.getLoop()));
    next.setLoopAt(next.getEndAt());
    next.setCompleteAt(null);
    next.setUserId(actorUserId);
    next.setCreatedAt(now);
    next.setUpdatedAt(now);
    return next;
  }
}
