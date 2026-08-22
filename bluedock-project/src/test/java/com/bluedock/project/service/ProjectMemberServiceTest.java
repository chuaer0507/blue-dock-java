package com.bluedock.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.project.domain.Project;
import com.bluedock.project.repo.ProjectInviteRepository;
import com.bluedock.project.repo.ProjectRepository;
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
class ProjectMemberServiceTest {
  @Mock ProjectRepository projects;
  @Mock ProjectInviteRepository invites;
  @Mock ProjectAccessService access;
  @Mock UserAccountRepository users;
  @Mock ProjectService projectService;
  @Mock ProjectLogService projectLogs;
  @Mock com.bluedock.common.project.TaskProjectArchiveBridge archiveBridge;
  @InjectMocks ProjectMemberService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void transfer_ok() {
    when(access.requireOwner(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    Project p = teamProject(9L);
    when(projects.findActive(9L)).thenReturn(Optional.of(p));
    when(access.requireMember(9L, 2L)).thenReturn(ProjectAccessService.OWNER_MEMBER);
    when(users.existsByUserId(2L)).thenReturn(true);

    service.transfer(9L, 2L);

    verify(projects).updateMemberOwner(9L, 1L, ProjectAccessService.OWNER_MEMBER);
    verify(projects).updateMemberOwner(9L, 2L, ProjectAccessService.OWNER_OWNER);
  }

  @Test
  void exit_ownerRejected() {
    when(access.requireMember(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    assertThrows(BusinessException.class, () -> service.exit(9L));
  }

  @Test
  void updateMembers_add() {
    when(access.requireManage(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    when(projects.findActive(9L)).thenReturn(Optional.of(teamProject(9L)));
    when(users.existsByUserId(2L)).thenReturn(true);
    when(access.findOwner(9L, 2L)).thenReturn(Optional.empty());
    when(projects.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    var view = service.updateMembers(9L, "2", null);
    assertEquals(List.of(1L, 2L), view.userIds());
    verify(projects).insertMember(anyLong(), eq(9L), eq(2L), eq(ProjectAccessService.OWNER_MEMBER));
  }

  @Test
  void personal_rejected() {
    when(access.requireManage(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    Project p = teamProject(9L);
    p.setIsPersonal(1);
    when(projects.findActive(9L)).thenReturn(Optional.of(p));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.updateMembers(9L, "2", null));
    assertEquals(ErrorCodes.BAD_REQUEST, ex.getCode());
  }

  @Test
  void archive_add_and_recovery() {
    when(access.requireOwner(9L, 1L)).thenReturn(ProjectAccessService.OWNER_OWNER);
    Project p = teamProject(9L);
    when(projects.findActive(9L)).thenReturn(Optional.of(p));

    var archived = service.archive(9L, "add");
    org.junit.jupiter.api.Assertions.assertNotNull(archived.archivedAt());
    verify(projects).archive(9L, 1L);
    verify(archiveBridge).archiveByProject(eq(9L), eq(1L), any());

    p.setArchivedAt(java.time.LocalDateTime.now());
    var recovered = service.archive(9L, "recovery");
    org.junit.jupiter.api.Assertions.assertNull(recovered.archivedAt());
    verify(projects).unarchive(9L, 1L);
    verify(archiveBridge).unarchiveByProject(9L, 1L);
  }

  private static Project teamProject(long id) {
    Project p = new Project();
    p.setId(id);
    p.setName("P");
    p.setIsPersonal(0);
    return p;
  }
}
