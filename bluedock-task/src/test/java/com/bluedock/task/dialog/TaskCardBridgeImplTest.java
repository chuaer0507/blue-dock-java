package com.bluedock.task.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import com.bluedock.task.service.TaskRelationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCardBridgeImplTest {
  @Mock TaskRepository tasks;
  @Mock TaskVisibilityUserRepository visibilityUsers;
  @Mock ProjectAccessService access;
  @Mock TaskRelationService relations;
  @InjectMocks TaskCardBridgeImpl bridge;

  @Test
  void buildCard_ok() {
    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("Demo");
    t.setVisibility(1);
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);

    Map<String, Object> card = bridge.buildCard(50L, 1L, "hi");
    assertEquals(50L, card.get("taskId"));
    assertEquals("Demo", card.get("name"));
    assertEquals("hi", card.get("note"));
  }

  @Test
  void buildCard_vis2_deniesNonAssignee() {
    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setVisibility(2);
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(tasks.isAssignee(50L, 1L)).thenReturn(false);
    assertThrows(BusinessException.class, () -> bridge.buildCard(50L, 1L, null));
  }

  @Test
  void linkFromDialogIfTaskGroup_links() {
    when(tasks.listIdsByDialogId(9L)).thenReturn(List.of(7L));
    when(relations.link(7L, 50L, 9L, 100L, 1L)).thenReturn(true);
    bridge.linkFromDialogIfTaskGroup(9L, 100L, 50L, 1L);
    verify(relations).link(7L, 50L, 9L, 100L, 1L);
  }
}
