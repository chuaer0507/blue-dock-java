package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bluedock.task.domain.TaskItem;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TaskRecurringTest {
  @Test
  void shift_dayWeekMonthYear() {
    LocalDateTime base = LocalDateTime.of(2026, 1, 31, 10, 0);
    assertEquals(LocalDateTime.of(2026, 2, 1, 10, 0), TaskRecurring.shift(base, TaskRecurring.DAY));
    assertEquals(LocalDateTime.of(2026, 2, 7, 10, 0), TaskRecurring.shift(base, TaskRecurring.WEEK));
    assertEquals(LocalDateTime.of(2026, 2, 28, 10, 0), TaskRecurring.shift(base, TaskRecurring.MONTH));
    assertEquals(LocalDateTime.of(2027, 1, 31, 10, 0), TaskRecurring.shift(base, TaskRecurring.YEAR));
    assertNull(TaskRecurring.shift(null, TaskRecurring.DAY));
  }

  @Test
  void buildNext_copiesAndShifts() {
    TaskItem done = new TaskItem();
    done.setId(1L);
    done.setProjectId(10L);
    done.setColumnId(20L);
    done.setName("周报");
    done.setDescription("d");
    done.setColor("#f00");
    done.setVisibility(2);
    done.setPriorityLevel(1);
    done.setPriorityName("高");
    done.setPriorityColor("#00f");
    done.setLoop(TaskRecurring.WEEK);
    done.setStartAt(LocalDateTime.of(2026, 8, 3, 9, 0));
    done.setEndAt(LocalDateTime.of(2026, 8, 7, 18, 0));
    done.setCompleteAt(LocalDateTime.now());

    LocalDateTime now = LocalDateTime.of(2026, 8, 7, 19, 0);
    TaskItem next = TaskRecurring.buildNext(done, 99L, 7L, now, 4);
    assertEquals(99L, next.getId());
    assertEquals(0L, next.getParentId());
    assertEquals(0L, next.getDialogId());
    assertEquals(0L, next.getFlowItemId());
    assertEquals("周报", next.getName());
    assertEquals(TaskRecurring.WEEK, next.getLoop());
    assertEquals(LocalDateTime.of(2026, 8, 10, 9, 0), next.getStartAt());
    assertEquals(LocalDateTime.of(2026, 8, 14, 18, 0), next.getEndAt());
    assertEquals(next.getEndAt(), next.getLoopAt());
    assertNull(next.getCompleteAt());
    assertEquals(7L, next.getUserId());
    assertEquals(4, next.getSort());
  }
}
