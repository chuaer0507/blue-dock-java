package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectColumnView;
import com.bluedock.project.web.dto.ProjectView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {
  @Mock ProjectRepository projects;
  @Mock ProjectColumnRepository columns;
  @Mock ProjectAccessService access;
  @Mock ProjectPermissionService projectPermissions;
  @Mock ProjectLogService projectLogs;
  @Mock SearchIndexPublisher searchIndex;
  @Mock ObjectMapper objectMapper;
  @Mock ObjectProvider<RealtimeFanoutPublisher> realtimeFanout;
  ProjectService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
    org.mockito.Mockito.lenient().when(realtimeFanout.getIfAvailable()).thenReturn(null);
    service =
        new ProjectService(
            projects,
            columns,
            access,
            projectPermissions,
            projectLogs,
            searchIndex,
            objectMapper,
            null,
            null,
            realtimeFanout);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void lists_filtersByArchivedTypeAndName() {
    when(projects.listForUser(1L, "yes", "team", "官网")).thenReturn(List.of());
    service.lists("yes", "team", "官网", null);
    verify(projects).listForUser(1L, "yes", "team", "官网");
  }

  @Test
  void lists_keysJsonName() throws Exception {
    when(objectMapper.readValue(eq("{\"name\":\"A\"}"), eq(Map.class)))
        .thenReturn(Map.of("name", "A"));
    when(projects.listForUser(1L, "no", "all", "A")).thenReturn(List.of());
    service.lists("no", "all", null, "{\"name\":\"A\"}");
    verify(projects).listForUser(1L, "no", "all", "A");
  }

  @Test
  void lists_invalidArchived() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.lists("maybe", "all", null, null));
    assertEquals(I18nKeys.PROJECT_LIST_ARCHIVED_INVALID, ex.getMessageKey());
  }

  @Test
  void add_teamProject_createsDefaultColumns() {
    ProjectView view = service.add("Demo", "desc", 0);

    assertEquals("Demo", view.name());
    assertEquals(0, view.isPersonal());
    verify(projects).insert(any(Project.class));
    verify(projects).insertMember(anyLong(), anyLong(), eq(1L), eq(ProjectAccessService.OWNER_OWNER));
    verify(columns, times(3)).insert(any());
  }

  @Test
  void add_withColumnsCsv_usesTemplateNames() {
    service.add("Tpl", null, 0, "产品规划,前端开发,后端开发");
    verify(columns, times(3)).insert(any());
  }

  @Test
  void add_columnsOverLimit_rejected() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 31; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append("C").append(i);
    }
    assertThrows(BusinessException.class, () -> service.add("Big", null, 0, sb.toString()));
  }

  @Test
  void add_secondPersonal_rejected() {
    when(projects.countPersonalForUser(1L)).thenReturn(1);
    assertThrows(BusinessException.class, () -> service.add("Mine", null, 1));
  }

  @Test
  void update_requiresManage() {
    when(access.requireManage(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    Project p = new Project();
    p.setId(9L);
    p.setName("Old");
    p.setDescription("");
    when(projects.findActive(9L)).thenReturn(Optional.of(p));

    ProjectView view = service.update(9L, "New", null, null, null, null, null, null);
    assertEquals("New", view.name());
    verify(projects).update(p);
  }

  @Test
  void update_archiveSettings() {
    when(access.requireManage(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    Project p = new Project();
    p.setId(9L);
    p.setName("P");
    p.setDescription("");
    when(projects.findActive(9L)).thenReturn(Optional.of(p));

    ProjectView view =
        service.update(9L, null, null, "custom", 14, "close", "close", "close");
    assertEquals("custom", view.archiveMethod());
    assertEquals(14, view.archiveDays());
    assertEquals("close", view.aiAutoAnalyze());
    assertEquals("close", view.taskTemplateShare());
    assertEquals("close", view.departmentOwnerView());
  }

  @Test
  void update_denied() {
    when(access.requireManage(anyLong(), anyLong()))
        .thenThrow(new BusinessException(ErrorCodes.PROJECT_DENIED, I18nKeys.PROJECT_MANAGE_REQUIRED));
    assertThrows(
        BusinessException.class,
        () -> service.update(9L, "X", null, null, null, null, null, null));
  }

  @Test
  void columnRemove_softDeletes() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    col.setName("Doing");
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(columns.countActiveByProject(10L)).thenReturn(3);

    service.columnRemove(20L);

    verify(columns).softDelete(20L);
  }

  @Test
  void columnRemove_lastColumn_rejected() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(columns.countActiveByProject(10L)).thenReturn(1);

    BusinessException ex = assertThrows(BusinessException.class, () -> service.columnRemove(20L));
    assertEquals(I18nKeys.PROJECT_COLUMN_LAST, ex.getMessageKey());
  }

  @Test
  void columnOne_ok() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    col.setName("Doing");
    col.setColor("#409EFF");
    col.setSort(1);
    when(columns.findActive(20L)).thenReturn(Optional.of(col));

    ProjectColumnView view = service.columnOne(20L);
    assertEquals(20L, view.id());
    assertEquals("Doing", view.name());
    assertEquals("#409EFF", view.color());
  }

  @Test
  void columnOne_notFound() {
    when(columns.findActive(20L)).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> service.columnOne(20L));
  }

  @Test
  void columnAdd_publishesCreatedFanout() {
    RealtimeFanoutPublisher publisher = org.mockito.Mockito.mock(RealtimeFanoutPublisher.class);
    when(realtimeFanout.getIfAvailable()).thenReturn(publisher);
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(access.listMemberUserIds(10L)).thenReturn(List.of(1L, 2L));
    when(projects.findActive(10L)).thenReturn(Optional.of(new Project()));
    when(columns.listByProject(10L)).thenReturn(List.of());

    ProjectColumnView view = service.columnAdd(10L, "Backlog", "#909399");
    assertEquals("Backlog", view.name());

    ArgumentCaptor<RealtimeFanoutEvent> cap = ArgumentCaptor.forClass(RealtimeFanoutEvent.class);
    verify(publisher).publish(cap.capture());
    assertEquals(RealtimeEventTypes.COLUMN_CREATED, cap.getValue().type());
    assertEquals(List.of(1L, 2L), cap.getValue().userIds());
  }
}
