package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.org.department.repo.DepartmentRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.DashboardTeamStatsView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
  @Mock ProjectRepository projects;
  @Mock TaskRepository tasks;
  @Mock DepartmentRepository departments;
  @InjectMocks DashboardService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void teamStats_fallback_projectOwner() {
    when(projects.listManagedProjectIds(1L)).thenReturn(List.of(10L));
    when(projects.listDistinctMemberUserIds(List.of(10L))).thenReturn(List.of(1L, 2L));

    TaskItem open = new TaskItem();
    open.setId(1L);
    open.setEndAt(LocalDateTime.now().minusDays(1));
    open.setPriorityLevel(3);

    TaskItem done = new TaskItem();
    done.setId(2L);
    done.setCompleteAt(LocalDateTime.now());

    when(tasks.listTeamTasks(List.of(10L), List.of(1L, 2L))).thenReturn(List.of(open, done));

    DashboardTeamStatsView stats = service.teamStats(null);
    assertEquals(1, stats.uncompleted());
    assertEquals(1, stats.overdue());
    assertEquals(1, stats.weekCompleted());
  }

  @Test
  void teamStats_departmentScope() {
    when(departments.canManage(1L, 5L)).thenReturn(true);
    when(departments.listActiveMemberIdsInTree(5L)).thenReturn(List.of(2L, 3L));
    when(projects.listProjectIdsForDepartmentOwnerView(List.of(2L, 3L))).thenReturn(List.of(20L));
    when(tasks.listTeamTasks(List.of(20L), List.of(2L, 3L))).thenReturn(List.of());

    DashboardTeamStatsView stats = service.teamStats(5L);
    assertEquals(0, stats.uncompleted());
    assertEquals(List.of(2L, 3L), stats.memberUserIds());
    assertEquals(List.of(20L), stats.projectIds());
  }

  @Test
  void teamStats_departmentDenied() {
    when(departments.canManage(1L, 9L)).thenReturn(false);
    assertThrows(BusinessException.class, () -> service.teamStats(9L));
  }
}
