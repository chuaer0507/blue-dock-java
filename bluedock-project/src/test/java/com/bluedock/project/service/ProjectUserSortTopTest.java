package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import java.time.LocalDateTime;
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
class ProjectUserSortTopTest {
  @Mock
  ProjectRepository projects;
  @Mock
  ProjectColumnRepository columns;
  @Mock
  ProjectAccessService access;
  @Mock
  ProjectPermissionService projectPermissions;
  @Mock
  ProjectLogService projectLogs;
  @Mock
  SearchIndexPublisher searchIndex;
  @Mock
  ObjectProvider<com.bluedock.common.realtime.RealtimeFanoutPublisher> realtimeFanout;

  ProjectService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(7L));
    when(access.requireMember(anyLong(), anyLong())).thenReturn(0);
    when(realtimeFanout.getIfAvailable()).thenReturn(null);
    service =
        new ProjectService(
            projects,
            columns,
            access,
            projectPermissions,
            projectLogs,
            searchIndex,
            new ObjectMapper(),
            null,
            null,
            realtimeFanout);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void userSort_updatesOwnRows() {
    service.userSort(List.of(12, 5, "9", 0, "x"));
    verify(projects).updateMemberSort(7L, 12L, 0);
    verify(projects).updateMemberSort(7L, 5L, 1);
    verify(projects).updateMemberSort(7L, 9L, 2);
  }

  @Test
  void userSort_nullRejected() {
    assertThrows(BusinessException.class, () -> service.userSort(null));
  }

  @Test
  void top_toggles() {
    LocalDateTime now = LocalDateTime.of(2026, 8, 4, 12, 0);
    when(projects.toggleMemberTop(7L, 3L))
        .thenReturn(Optional.of(new ProjectRepository.TopToggle(now)));
    Map<String, Object> pinned = service.top(3L);
    assertEquals(3L, pinned.get("id"));
    assertEquals(now, pinned.get("topAt"));

    when(projects.toggleMemberTop(7L, 3L))
        .thenReturn(Optional.of(new ProjectRepository.TopToggle(null)));
    Map<String, Object> unpinned = service.top(3L);
    assertNull(unpinned.get("topAt"));
  }
}
