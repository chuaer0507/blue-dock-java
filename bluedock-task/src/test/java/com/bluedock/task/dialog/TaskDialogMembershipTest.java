package com.bluedock.task.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskDialogMembershipTest {
  @Mock TaskRepository tasks;
  @Mock TaskVisibilityUserRepository visibilityUsers;
  @Mock ProjectRepository projects;
  @Mock TaskGroupBridge groupBridge;
  @InjectMocks TaskDialogMembership membership;

  @Test
  void resolve_vis1_includesProjectMembers() {
    TaskItem t = task(1);
    when(tasks.listAssigneeUserIds(5L)).thenReturn(List.of(1L));
    when(projects.listMemberUserIds(10L)).thenReturn(List.of(1L, 2L, 3L));
    assertEquals(Set.of(1L, 2L, 3L), membership.resolveMembers(t));
  }

  @Test
  void resolve_vis2_assigneesOnly() {
    TaskItem t = task(2);
    when(tasks.listAssigneeUserIds(5L)).thenReturn(List.of(1L, 8L));
    assertEquals(Set.of(1L, 8L), membership.resolveMembers(t));
  }

  @Test
  void resolve_vis3_assigneesPlusVisibilityUsers() {
    TaskItem t = task(3);
    when(tasks.listAssigneeUserIds(5L)).thenReturn(List.of(1L));
    when(visibilityUsers.listUserIds(5L)).thenReturn(List.of(9L, 10L));
    assertEquals(Set.of(1L, 9L, 10L), membership.resolveMembers(t));
  }

  @Test
  void syncIfPresent_callsEnsureGroup() {
    TaskItem t = task(2);
    t.setDialogId(77L);
    when(tasks.listAssigneeUserIds(5L)).thenReturn(List.of(1L));
    when(groupBridge.ensureGroup(5L, "T", 1L, Set.of(1L))).thenReturn(77L);
    membership.syncIfPresent(t);
    verify(groupBridge).ensureGroup(5L, "T", 1L, Set.of(1L));
  }

  @Test
  void access_vis2_deniesNonAssignee() {
    TaskDialogAccessBridgeImpl access =
        new TaskDialogAccessBridgeImpl(tasks, visibilityUsers, projects);
    TaskItem t = task(2);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(projects.findMemberOwner(10L, 99L)).thenReturn(Optional.of(0));
    when(tasks.isAssignee(5L, 99L)).thenReturn(false);
    assertFalse(access.canAccessTaskDialog(5L, 99L));
  }

  @Test
  void access_vis1_allowsProjectMember() {
    TaskDialogAccessBridgeImpl access =
        new TaskDialogAccessBridgeImpl(tasks, visibilityUsers, projects);
    TaskItem t = task(1);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(projects.findMemberOwner(10L, 2L)).thenReturn(Optional.of(0));
    assertTrue(access.canAccessTaskDialog(5L, 2L));
  }

  private static TaskItem task(int visibility) {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setUserId(1L);
    t.setName("T");
    t.setVisibility(visibility);
    t.setDialogId(0L);
    return t;
  }
}
