package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.org.department.repo.DepartmentRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskRepository.UserTaskRow;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskCounts;
import com.bluedock.task.web.dto.ProjectUserTaskDtos.UserTaskPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectUserTaskServiceTest {
  @Mock TaskRepository tasks;
  @Mock ProjectRepository projects;
  @Mock DepartmentRepository departments;
  @Mock UserAccountRepository users;
  @Mock AdminGuard adminGuard;
  @Mock SystemGeneralSettingService systemSettings;
  @InjectMocks ProjectUserTaskService service;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    AuthContext.set(new AuthUser(1L));
    var f = ProjectUserTaskService.class.getDeclaredField("objectMapper");
    f.setAccessible(true);
    f.set(service, objectMapper);
    when(systemSettings.isDepartmentOwnerProjectViewOpen()).thenReturn(true);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void counts_self() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setIsBot(0);
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(projects.countProjectsForUser(1L, null)).thenReturn(3L);
    when(tasks.countForUser(eq(1L), isNull(), isNull(), isNull(), eq("uncompleted"), isNull(), eq(false)))
        .thenReturn(5L);
    when(tasks.countForUser(eq(1L), isNull(), isNull(), isNull(), eq("completed"), isNull(), eq(false)))
        .thenReturn(2L);

    UserTaskCounts c = service.counts(1L, null);
    assertEquals(3L, c.project());
    assertEquals(5L, c.todo());
    assertEquals(2L, c.done());
  }

  @Test
  void projects_self() {
    UserAccount u = new UserAccount();
    u.setUserId(1L);
    u.setIsBot(0);
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    com.bluedock.project.domain.Project p = new com.bluedock.project.domain.Project();
    p.setId(7L);
    p.setName("P");
    p.setUserId(1L);
    when(projects.countForUser(eq(1L), eq("no"), eq("all"), isNull(), isNull())).thenReturn(1L);
    when(projects.listForUser(eq(1L), eq("no"), eq("all"), isNull(), isNull(), eq(0), eq(50)))
        .thenReturn(List.of(p));

    var page = service.projects(1L, "no", null, 1, 50);
    assertEquals(1, page.items().size());
    assertEquals(7L, page.items().get(0).id());
    assertEquals(false, page.items().get(0).departmentReadonly());
  }

  @Test
  void tasks_deptReadonly() {
    UserAccount u = new UserAccount();
    u.setUserId(9L);
    u.setIsBot(0);
    when(users.findByUserId(9L)).thenReturn(Optional.of(u));
    when(adminGuard.isAdmin(1L)).thenReturn(false);
    when(departments.listManagedMemberUserIds(1L)).thenReturn(List.of(9L));
    when(projects.listProjectIdsForUserOwnerView(9L)).thenReturn(List.of(10L));

    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(1L);
    t.setName("A");
    t.setVisibility(1);
    t.setEndAt(LocalDateTime.now().minusDays(1));
    when(tasks.countForUser(
            eq(9L), eq(1), isNull(), isNull(), isNull(), eq(List.of(10L)), eq(true)))
        .thenReturn(1L);
    when(tasks.listForUser(
            eq(9L),
            eq(1),
            isNull(),
            isNull(),
            isNull(),
            eq(List.of(10L)),
            eq(true),
            anyInt(),
            anyInt()))
        .thenReturn(List.of(new UserTaskRow(t, 1, "Proj")));

    UserTaskPage page = service.tasks(9L, 1, null, null, 1, 20);
    assertEquals(1, page.items().size());
    assertTrue(page.items().get(0).departmentReadonly());
    assertTrue(page.items().get(0).overdue());
    assertEquals("Proj", page.items().get(0).projectName());
  }

  @Test
  void counts_deniedForUnmanaged() {
    UserAccount u = new UserAccount();
    u.setUserId(9L);
    u.setIsBot(0);
    when(users.findByUserId(9L)).thenReturn(Optional.of(u));
    when(adminGuard.isAdmin(1L)).thenReturn(false);
    when(departments.listManagedMemberUserIds(1L)).thenReturn(List.of());
    assertThrows(BusinessException.class, () -> service.counts(9L, null));
  }

  @Test
  void counts_botDenied() {
    UserAccount u = new UserAccount();
    u.setUserId(2L);
    u.setIsBot(1);
    when(users.findByUserId(2L)).thenReturn(Optional.of(u));
    assertThrows(BusinessException.class, () -> service.counts(2L, null));
  }
}
