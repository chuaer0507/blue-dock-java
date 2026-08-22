package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.domain.TaskRelation;
import com.bluedock.task.repo.TaskRelationRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.TaskRelatedListView;
import java.time.LocalDateTime;
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
class TaskRelationServiceTest {
  @Mock TaskRepository tasks;
  @Mock TaskRelationRepository relations;
  @Mock ProjectAccessService access;
  @Mock ProjectRepository projects;
  @Mock ProjectColumnRepository columns;
  @InjectMocks TaskRelationService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void add_createsBidirectional() {
    TaskItem a = task(5L, 10L);
    TaskItem b = task(6L, 10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(a));
    when(tasks.findActive(6L)).thenReturn(Optional.of(b));
    when(access.requireMember(10L, 1L)).thenReturn(0);

    Map<String, Object> out = service.add(5L, 6L);
    assertEquals(5L, out.get("taskId"));
    verify(relations)
        .upsert(
            anyLong(),
            eq(5L),
            eq(6L),
            eq(TaskRelation.DIRECTION_MENTION),
            eq(0L),
            eq(0L),
            eq(1L));
    verify(relations)
        .upsert(
            anyLong(),
            eq(6L),
            eq(5L),
            eq(TaskRelation.DIRECTION_MENTIONED_BY),
            eq(0L),
            eq(0L),
            eq(1L));
  }

  @Test
  void add_selfRejected() {
    assertThrows(BusinessException.class, () -> service.add(5L, 5L));
  }

  @Test
  void list_mergesDirections() {
    TaskItem a = task(5L, 10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(a));
    when(access.requireMember(10L, 1L)).thenReturn(0);

    TaskRelation mention = new TaskRelation();
    mention.setRelatedTaskId(6L);
    mention.setDirection(TaskRelation.DIRECTION_MENTION);
    mention.setUpdatedAt(LocalDateTime.parse("2026-01-02T00:00:00"));
    mention.setMessageId(9L);
    TaskRelation reverse = new TaskRelation();
    reverse.setRelatedTaskId(6L);
    reverse.setDirection(TaskRelation.DIRECTION_MENTIONED_BY);
    reverse.setUpdatedAt(LocalDateTime.parse("2026-01-03T00:00:00"));
    reverse.setMessageId(10L);
    when(relations.listByTask(5L, 100)).thenReturn(List.of(mention, reverse));

    TaskItem related = task(6L, 10L);
    related.setName("关联");
    related.setColumnId(20L);
    when(tasks.findActive(6L)).thenReturn(Optional.of(related));
    Project p = new Project();
    p.setName("P");
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    ProjectColumn c = new ProjectColumn();
    c.setName("Col");
    when(columns.findActive(20L)).thenReturn(Optional.of(c));

    TaskRelatedListView view = service.list(5L);
    assertEquals(1, view.items().size());
    Map<String, Object> item = view.items().get(0);
    assertTrue((Boolean) item.get("mention"));
    assertTrue((Boolean) item.get("mentionedBy"));
    assertEquals(10L, item.get("latestMessageId"));
  }

  @Test
  void delete_ok() {
    when(tasks.findActive(5L)).thenReturn(Optional.of(task(5L, 10L)));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(relations.deletePair(5L, 6L)).thenReturn(2);
    service.delete(5L, 6L);
    verify(relations).deletePair(5L, 6L);
  }

  private static TaskItem task(long id, long projectId) {
    TaskItem t = new TaskItem();
    t.setId(id);
    t.setProjectId(projectId);
    t.setColumnId(1L);
    t.setName("T" + id);
    return t;
  }
}
