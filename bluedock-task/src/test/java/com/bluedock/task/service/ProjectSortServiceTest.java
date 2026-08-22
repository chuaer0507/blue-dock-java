package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.project.service.ProjectPermissionService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectSortServiceTest {
  @Mock
  ProjectAccessService access;
  @Mock
  ProjectPermissionService projectPermissions;
  @Mock
  ProjectLogService projectLogs;
  @Mock
  ProjectColumnRepository columns;
  @Mock
  TaskRepository tasks;
  @Mock
  TaskColumnFlowSync columnFlowSync;
  @Mock
  ObjectProvider<RealtimeFanoutPublisher> realtimeFanout;

  ProjectSortService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    when(access.requireMember(anyLong(), anyLong())).thenReturn(0);
    when(realtimeFanout.getIfAvailable()).thenReturn(null);
    service =
        new ProjectSortService(
            access,
            projectPermissions,
            columns,
            tasks,
            projectLogs,
            columnFlowSync,
            new ObjectMapper(),
            realtimeFanout);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void sortColumns_only() {
    service.sort(
        10L,
        true,
        List.of(Map.of("id", 101), Map.of("id", 102), Map.of("id", "103")));
    verify(columns).updateSort(101L, 10L, 0);
    verify(columns).updateSort(102L, 10L, 1);
    verify(columns).updateSort(103L, 10L, 2);
    verify(tasks, never()).updateColumnAndSortIfIncomplete(anyLong(), anyLong(), anyLong(), anyInt());
  }

  @Test
  void sortTasks_updatesAndMovesChildren() {
    ProjectColumn col = new ProjectColumn();
    col.setId(201L);
    col.setProjectId(10L);
    when(columns.findActive(201L)).thenReturn(Optional.of(col));

    TaskItem t = new TaskItem();
    t.setId(501L);
    t.setProjectId(10L);
    t.setColumnId(199L);
    when(tasks.findActive(501L)).thenReturn(Optional.of(t));
    when(tasks.updateColumnAndSortIfIncomplete(501L, 10L, 201L, 0)).thenReturn(1);

    service.sort(10L, false, List.of(Map.of("id", 201, "task", List.of(501, 502))));

    verify(tasks).updateColumnAndSortIfIncomplete(501L, 10L, 201L, 0);
    verify(tasks).moveChildrenLocation(501L, 10L, 201L);
    verify(columnFlowSync).syncAfterColumnMove(501L, 10L, 201L);
    verify(tasks).updateColumnAndSortIfIncomplete(502L, 10L, 201L, 1);
  }

  @Test
  void sortTasks_skipsCompleted() {
    ProjectColumn col = new ProjectColumn();
    col.setId(201L);
    col.setProjectId(10L);
    when(columns.findActive(201L)).thenReturn(Optional.of(col));
    when(tasks.updateColumnAndSortIfIncomplete(501L, 10L, 201L, 0)).thenReturn(0);

    service.sort(10L, false, List.of(Map.of("id", 201, "task", List.of(501))));

    verify(tasks, never()).moveChildrenLocation(anyLong(), anyLong(), anyLong());
  }

  @Test
  void sort_invalidPayload() {
    assertThrows(BusinessException.class, () -> service.sort(10L, false, "not-json"));
    assertThrows(BusinessException.class, () -> service.sort(0L, false, List.of()));
  }
}
