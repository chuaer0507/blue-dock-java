package com.bluedock.task.column;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskColumnCascadeBridgeImplTest {
  @Mock TaskRepository tasks;
  @Mock SearchIndexPublisher searchIndex;
  @Mock TaskGroupBridge groupBridge;

  TaskColumnCascadeBridgeImpl bridge;

  @BeforeEach
  void setUp() {
    bridge = new TaskColumnCascadeBridgeImpl(tasks, searchIndex, groupBridge);
  }

  @Test
  void softDeleteByColumn_disbandsAndDeletes() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("T");
    t.setUserId(1L);
    when(tasks.listByProject(10L, 20L, true)).thenReturn(List.of(t));

    bridge.softDeleteByColumn(10L, 20L, 1L);

    verify(groupBridge).disbandByLink(5L);
    verify(tasks).softDeleteByColumn(10L, 20L, 1L);
  }

  @Test
  void softDeleteByColumn_emptyColumn() {
    when(tasks.listByProject(10L, 20L, true)).thenReturn(List.of());

    bridge.softDeleteByColumn(10L, 20L, 1L);

    verify(groupBridge, never()).disbandByLink(anyLong());
    verify(tasks).softDeleteByColumn(eq(10L), eq(20L), eq(1L));
  }
}
