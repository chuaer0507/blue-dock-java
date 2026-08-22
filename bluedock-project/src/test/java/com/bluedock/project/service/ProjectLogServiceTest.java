package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.project.domain.ProjectLog;
import com.bluedock.project.repo.ProjectLogRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectLogDtos.ProjectLogPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectLogServiceTest {
  @Mock
  ProjectLogRepository logs;
  @Mock
  ProjectRepository projects;
  @Mock
  ProjectAccessService access;

  ProjectLogService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
    service = new ProjectLogService(logs, projects, access, new ObjectMapper());
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void recordProject_inserts() {
    service.recordProject(10L, 0L, "创建项目", null);
    ArgumentCaptor<ProjectLog> cap = ArgumentCaptor.forClass(ProjectLog.class);
    verify(logs).insert(cap.capture());
    assertEquals(10L, cap.getValue().getProjectId());
    assertEquals("创建项目", cap.getValue().getDetail());
    assertEquals(1L, cap.getValue().getUserId());
    assertEquals(0, cap.getValue().getTaskOnly());
  }

  @Test
  void recordTask_replacesPlaceholderAndSubtask() {
    service.recordTask(10L, 2L, 99L, 50L, "子A", "创建{任务}", null, 0);
    ArgumentCaptor<ProjectLog> cap = ArgumentCaptor.forClass(ProjectLog.class);
    verify(logs).insert(cap.capture());
    assertEquals(50L, cap.getValue().getTaskId());
    assertEquals("创建子任务", cap.getValue().getDetail());
    assertEquals(true, cap.getValue().getRecordJson().contains("\"id\":99"));
  }

  @Test
  void lists_byProject() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(projects.findActive(10L)).thenReturn(Optional.of(new com.bluedock.project.domain.Project()));
    when(logs.countByProject(10L)).thenReturn(1L);
    ProjectLog row = new ProjectLog();
    row.setId(1L);
    row.setProjectId(10L);
    row.setUserId(1L);
    row.setDetail("创建项目");
    row.setCreatedAt(LocalDateTime.of(2026, 8, 4, 10, 30));
    when(logs.listByProject(eq(10L), anyInt(), anyInt())).thenReturn(List.of(row));

    ProjectLogPage page = service.lists(10L, null, 1, 20);
    assertEquals(1, page.items().size());
    assertEquals("创建项目", page.items().get(0).detail());
    assertEquals(1, page.meta().totalSize());
    assertEquals("上午", page.items().get(0).time().segment());
  }

  @Test
  void lists_missingParam() {
    BusinessException ex = assertThrows(BusinessException.class, () -> service.lists(null, null, 1, 20));
    assertEquals(I18nKeys.PROJECT_LOG_PARAM_REQUIRED, ex.getMessageKey());
  }

  @Test
  void lists_byTask() {
    when(logs.findTaskProjectId(99L)).thenReturn(Optional.of(10L));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(logs.countByTask(99L)).thenReturn(0L);
    when(logs.listByTask(eq(99L), anyInt(), anyInt())).thenReturn(List.of());
    ProjectLogPage page = service.lists(10L, 99L, 1, 20);
    assertEquals(0, page.meta().totalSize());
    verify(logs).listByTask(99L, 0, 20);
  }
}
