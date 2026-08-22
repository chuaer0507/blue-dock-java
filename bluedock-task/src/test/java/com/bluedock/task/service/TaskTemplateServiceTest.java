package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskTemplate;
import com.bluedock.task.repo.TaskTemplateRepository;
import com.bluedock.task.web.dto.TaskTemplateSearchPage;
import com.bluedock.task.web.dto.TaskTemplateView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTemplateServiceTest {
  @Mock TaskTemplateRepository templates;
  @Mock ProjectAccessService access;
  @Mock ProjectRepository projects;
  @InjectMocks TaskTemplateService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void save_create() {
    when(access.requireManage(10L, 1L)).thenReturn(1);
    when(templates.countByProject(10L)).thenReturn(0);
    when(templates.nextSort(10L)).thenReturn(0);

    TaskTemplateView view = service.save(10L, null, "日报", "标题", "内容");
    assertEquals("日报", view.name());
    assertEquals(10L, view.projectId());
    verify(templates).insert(any(TaskTemplate.class));
  }

  @Test
  void save_requiresBody() {
    when(access.requireManage(10L, 1L)).thenReturn(1);
    assertThrows(BusinessException.class, () -> service.save(10L, null, "名", "", ""));
  }

  @Test
  void toggleDefault() {
    when(access.requireManage(10L, 1L)).thenReturn(1);
    TaskTemplate t = new TaskTemplate();
    t.setId(5L);
    t.setProjectId(10L);
    t.setIsDefault(0);
    when(templates.find(5L)).thenReturn(Optional.of(t));

    Map<String, Object> out = service.toggleDefault(5L, 10L);
    assertEquals(1, out.get("isDefault"));
    verify(templates).clearDefault(10L);
    verify(templates).setDefault(5L, 10L, true);
  }

  @Test
  void visible_ok() {
    when(projects.listProjectIdsForUser(1L)).thenReturn(List.of(10L, 11L));
    when(projects.isTemplateShareOpen(10L)).thenReturn(true);
    TaskTemplate t = new TaskTemplate();
    t.setId(1L);
    t.setProjectId(10L);
    t.setName("A");
    when(templates.listByProjects(eq(List.of(10L, 11L)), eq(10L))).thenReturn(List.of(t));
    assertEquals(1, service.visible(10L).size());
  }

  @Test
  void search_paginates() {
    when(projects.listProjectIdsForUser(1L)).thenReturn(List.of(10L));
    when(projects.isTemplateShareOpen(10L)).thenReturn(true);
    when(templates.countSearch(eq(List.of(10L)), eq("日报"))).thenReturn(25L);
    TaskTemplate t = new TaskTemplate();
    t.setId(1L);
    t.setProjectId(10L);
    t.setName("日报");
    t.setUseCount(3);
    when(templates.search(eq(List.of(10L)), eq("日报"), eq(20), eq(20))).thenReturn(List.of(t));

    TaskTemplateSearchPage page = service.search("日报", 10L, 2, 20);
    assertEquals(1, page.items().size());
    assertEquals(2, page.meta().page());
    assertEquals(20, page.meta().pageSize());
    assertEquals(25L, page.meta().totalSize());
    assertEquals(2, page.meta().totalPage());
  }

  @Test
  void search_shareClosed_scopesToCurrentProject() {
    when(projects.listProjectIdsForUser(1L)).thenReturn(List.of(10L, 11L));
    when(projects.isTemplateShareOpen(10L)).thenReturn(false);
    when(templates.countSearch(eq(List.of(10L)), eq(""))).thenReturn(1L);
    when(templates.search(eq(List.of(10L)), eq(""), eq(0), eq(20))).thenReturn(List.of());

    service.search("", 10L, 1, 20);
    verify(templates).countSearch(eq(List.of(10L)), eq(""));
  }

  @Test
  void visible_shareClosed_scopesToCurrentProject() {
    when(projects.listProjectIdsForUser(1L)).thenReturn(List.of(10L, 11L));
    when(projects.isTemplateShareOpen(10L)).thenReturn(false);
    when(templates.listByProjects(eq(List.of(10L)), eq(10L))).thenReturn(List.of());
    service.visible(10L);
    verify(templates).listByProjects(eq(List.of(10L)), eq(10L));
  }

  @Test
  void recordUsage_incrementsWhenMember() {
    TaskTemplate t = new TaskTemplate();
    t.setId(9L);
    t.setProjectId(10L);
    when(templates.find(9L)).thenReturn(Optional.of(t));
    when(access.findOwner(10L, 1L)).thenReturn(Optional.of(0));

    service.recordUsage(9L, 10L);
    verify(templates).incrementUsage(9L);
  }

  @Test
  void recordUsage_skipsCrossProjectWhenShareClosed() {
    TaskTemplate t = new TaskTemplate();
    t.setId(9L);
    t.setProjectId(10L);
    when(templates.find(9L)).thenReturn(Optional.of(t));
    when(access.findOwner(10L, 1L)).thenReturn(Optional.of(0));
    when(projects.isTemplateShareOpen(11L)).thenReturn(false);

    service.recordUsage(9L, 11L);
    verify(templates, never()).incrementUsage(anyLong());
  }

  @Test
  void recordUsage_skipsWhenNotMember() {
    TaskTemplate t = new TaskTemplate();
    t.setId(9L);
    t.setProjectId(10L);
    when(templates.find(9L)).thenReturn(Optional.of(t));
    when(access.findOwner(10L, 1L)).thenReturn(Optional.empty());

    service.recordUsage(9L, 10L);
    verify(templates, never()).incrementUsage(anyLong());
  }
}
