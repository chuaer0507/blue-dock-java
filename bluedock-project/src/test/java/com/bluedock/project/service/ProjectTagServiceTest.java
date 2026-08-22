package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectTag;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.repo.ProjectTagRepository;
import com.bluedock.project.web.dto.ProjectTagView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectTagServiceTest {
  @Mock ProjectTagRepository tags;
  @Mock ProjectRepository projects;
  @Mock ProjectAccessService access;
  @Mock ProjectLogService projectLogs;
  @InjectMocks ProjectTagService service;

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
    when(access.requireManage(10L, 1L)).thenReturn(0);
    when(projects.findActive(10L)).thenReturn(Optional.of(new Project()));
    when(tags.countByProject(10L)).thenReturn(0);
    when(tags.findByProjectAndName(10L, "紧急")).thenReturn(Optional.empty());

    ProjectTagView view = service.save(10L, null, "紧急", "#F56C6C");
    assertEquals("紧急", view.name());
    assertEquals("#F56C6C", view.color());
    verify(tags).insert(any(ProjectTag.class));
  }

  @Test
  void save_duplicateName() {
    when(access.requireManage(10L, 1L)).thenReturn(0);
    when(projects.findActive(10L)).thenReturn(Optional.of(new Project()));
    when(tags.countByProject(10L)).thenReturn(1);
    when(tags.findByProjectAndName(10L, "紧急")).thenReturn(Optional.of(new ProjectTag()));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.save(10L, null, "紧急", null));
    assertEquals(I18nKeys.PROJECT_TAG_EXISTS, ex.getMessageKey());
  }

  @Test
  void sort_ok() {
    when(access.requireManage(10L, 1L)).thenReturn(0);
    service.sort(10L, List.of(3, 1, 2));
    verify(tags).updateSort(3L, 10L, 0);
    verify(tags).updateSort(1L, 10L, 1);
    verify(tags).updateSort(2L, 10L, 2);
  }

  @Test
  void delete_ok() {
    ProjectTag t = new ProjectTag();
    t.setId(9L);
    t.setProjectId(10L);
    when(tags.findActive(9L)).thenReturn(Optional.of(t));
    when(access.requireManage(10L, 1L)).thenReturn(0);
    service.delete(9L);
    verify(tags).softDelete(9L);
  }

  @Test
  void filterValidTagIds() {
    ProjectTag a = new ProjectTag();
    a.setId(1L);
    ProjectTag b = new ProjectTag();
    b.setId(2L);
    when(tags.listByIds(eq(10L), any())).thenReturn(List.of(a, b));
    assertEquals(List.of(1L, 2L), service.filterValidTagIds(10L, List.of(1L, 2L, 99L)));
  }
}
