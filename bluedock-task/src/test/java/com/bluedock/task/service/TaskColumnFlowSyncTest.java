package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.project.domain.ProjectFlowItem;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskColumnFlowSyncTest {
  @Mock ProjectFlowRepository flowItems;
  @Mock TaskRepository tasks;
  @InjectMocks TaskColumnFlowSync sync;

  @Test
  void syncAfterColumnMove_appliesBoundEnd() {
    ProjectFlowItem item = new ProjectFlowItem();
    item.setId(9L);
    item.setName("Done");
    item.setStatus("end");
    when(flowItems.findActiveItemByColumn(1L, 2L)).thenReturn(Optional.of(item));

    sync.syncAfterColumnMove(10L, 1L, 2L);

    verify(tasks).applyBoundFlowFromColumn(10L, 1L, 9L, "Done", true);
    verify(tasks).applyBoundFlowFromColumnForChildren(10L, 1L, 9L, "Done", true);
  }

  @Test
  void syncAfterColumnMove_noopWhenUnbound() {
    when(flowItems.findActiveItemByColumn(1L, 2L)).thenReturn(Optional.empty());
    sync.syncAfterColumnMove(10L, 1L, 2L);
    verify(tasks, never()).applyBoundFlowFromColumn(
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void applyBoundFlowToEntity_setsFlowWithoutClearingComplete() {
    ProjectFlowItem item = new ProjectFlowItem();
    item.setId(3L);
    item.setName("Doing");
    item.setStatus("progress");
    when(flowItems.findActiveItemByColumn(1L, 5L)).thenReturn(Optional.of(item));

    TaskItem t = new TaskItem();
    t.setProjectId(1L);
    t.setColumnId(5L);
    t.setCompleteAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
    sync.applyBoundFlowToEntity(t);

    assertEquals(3L, t.getFlowItemId());
    assertEquals("Doing", t.getFlowItemName());
    assertNotNull(t.getCompleteAt());
  }

  @Test
  void applyBoundFlowToEntity_endWritesComplete() {
    ProjectFlowItem item = new ProjectFlowItem();
    item.setId(4L);
    item.setName("End");
    item.setStatus("end");
    when(flowItems.findActiveItemByColumn(1L, 5L)).thenReturn(Optional.of(item));

    TaskItem t = new TaskItem();
    t.setProjectId(1L);
    t.setColumnId(5L);
    sync.applyBoundFlowToEntity(t);

    assertEquals(4L, t.getFlowItemId());
    assertNotNull(t.getCompleteAt());
  }

  @Test
  void applyBoundFlowToEntity_noopUnbound() {
    when(flowItems.findActiveItemByColumn(1L, 5L)).thenReturn(Optional.empty());
    TaskItem t = new TaskItem();
    t.setProjectId(1L);
    t.setColumnId(5L);
    sync.applyBoundFlowToEntity(t);
    assertEquals(0L, t.getFlowItemId());
    assertNull(t.getCompleteAt());
  }
}
