package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.project.domain.Project;
import com.bluedock.project.permission.ProjectPermissionCodes;
import com.bluedock.project.repo.ProjectPermissionRepository;
import com.bluedock.project.repo.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceTest {
  @Mock ProjectPermissionRepository permissions;
  @Mock ProjectRepository projects;
  @Mock ProjectAccessService access;
  @InjectMocks ProjectPermissionService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void get_returnsDefaults() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    Project p = new Project();
    p.setId(10L);
    p.setIsPersonal(0);
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    when(permissions.findJson(10L)).thenReturn(Optional.empty());

    Map<String, Object> view = service.get(10L);
    @SuppressWarnings("unchecked")
    Map<String, List<String>> matrix = (Map<String, List<String>>) view.get("permissions");
    assertTrue(matrix.get(ProjectPermissionCodes.ROLE_PROJECT_MEMBER).contains(ProjectPermissionCodes.TASK_ADD));
    assertFalse(
        matrix.get(ProjectPermissionCodes.ROLE_PROJECT_MEMBER).contains(ProjectPermissionCodes.TASK_LIST_ADD));
  }

  @Test
  void update_persists() {
    when(access.requireManage(10L, 1L)).thenReturn(1);
    Project p = new Project();
    p.setIsPersonal(0);
    when(projects.findActive(10L)).thenReturn(Optional.of(p));

    String raw =
        "{\"project_member\":[\"TASK_ADD\"],\"task_leader\":[\"TASK_UPDATE\"],\"task_assist\":[]}";
    service.update(10L, raw);

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(permissions).upsert(anyLong(), eq(10L), json.capture());
    assertTrue(json.getValue().contains("TASK_ADD"));
  }

  @Test
  void allows_ownerAlways() {
    when(access.findOwner(10L, 1L)).thenReturn(Optional.of(ProjectAccessService.OWNER_OWNER));
    assertTrue(service.allows(10L, 1L, ProjectPermissionCodes.TASK_LIST_REMOVE, null));
  }

  @Test
  void allows_memberDefault() {
    when(access.findOwner(10L, 1L)).thenReturn(Optional.of(ProjectAccessService.OWNER_MEMBER));
    when(permissions.findJson(10L)).thenReturn(Optional.empty());
    assertTrue(service.allows(10L, 1L, ProjectPermissionCodes.TASK_ADD, null));
    assertFalse(service.allows(10L, 1L, ProjectPermissionCodes.TASK_LIST_ADD, null));
  }

  @Test
  void update_personalRejected() {
    when(access.requireManage(10L, 1L)).thenReturn(1);
    Project p = new Project();
    p.setIsPersonal(1);
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.update(10L, "{}"));
    assertEquals(I18nKeys.PROJECT_PERMISSION_PERSONAL, ex.getMessageKey());
  }
}
