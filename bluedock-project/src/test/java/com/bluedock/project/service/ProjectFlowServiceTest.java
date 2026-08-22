package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectFlow;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.web.dto.ProjectFlowView;
import java.util.List;
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
class ProjectFlowServiceTest {
  @Mock
  ProjectFlowRepository flows;
  @Mock
  ProjectRepository projects;
  @Mock
  ProjectAccessService access;
  @InjectMocks
  ProjectFlowService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void save_defaultItems() {
    when(access.requireManage(10L, 1L)).thenReturn(0);
    Project p = new Project();
    p.setId(10L);
    p.setIsPersonal(0);
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    when(flows.listItemsByFlow(anyLong())).thenReturn(List.of());

    ProjectFlowView view = service.save(10L, null, "默认流程", null);

    assertEquals("默认流程", view.name());
    verify(flows).insertFlow(any(ProjectFlow.class));
    ArgumentCaptor<com.bluedock.project.domain.ProjectFlowItem> cap = ArgumentCaptor
        .forClass(com.bluedock.project.domain.ProjectFlowItem.class);
    verify(flows, org.mockito.Mockito.times(5)).insertItem(cap.capture());
    assertEquals(5, cap.getAllValues().size());
    assertEquals("待处理", cap.getAllValues().get(0).getName());
    assertEquals("start", cap.getAllValues().get(0).getStatus());
  }

  @Test
  void save_personalRejected() {
    when(access.requireManage(10L, 1L)).thenReturn(0);
    Project p = new Project();
    p.setId(10L);
    p.setIsPersonal(1);
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    BusinessException ex = assertThrows(BusinessException.class, () -> service.save(10L, null, "X", null));
    assertEquals(I18nKeys.PROJECT_FLOW_PERSONAL, ex.getMessageKey());
  }

  @Test
  void delete_ok() {
    ProjectFlow f = new ProjectFlow();
    f.setId(7L);
    f.setProjectId(10L);
    when(flows.findActiveFlow(7L)).thenReturn(Optional.of(f));
    when(access.requireManage(10L, 1L)).thenReturn(0);

    service.delete(7L);
    verify(flows).softDeleteFlow(7L);
  }
}
