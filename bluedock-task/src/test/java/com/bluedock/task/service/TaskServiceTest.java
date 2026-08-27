package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.project.domain.Project;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.project.repo.ProjectLogRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectFlowService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.project.service.ProjectPermissionService;
import com.bluedock.project.service.ProjectTagService;
import com.bluedock.task.dialog.TaskDialogMembership;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskFileRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskTagRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import com.bluedock.task.web.dto.TaskView;
import java.time.LocalDateTime;
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
class TaskServiceTest {
  @Mock TaskRepository tasks;
  @Mock TaskFileRepository taskFiles;
  @Mock TaskVisibilityUserRepository visibilityUsers;
  @Mock TaskTagRepository taskTags;
  @Mock ProjectAccessService access;
  @Mock ProjectRepository projects;
  @Mock ProjectColumnRepository columns;
  @Mock ProjectFlowRepository flowItems;
  @Mock ProjectFlowService projectFlows;
  @Mock ProjectTagService projectTags;
  @Mock ProjectPermissionService projectPermissions;
  @Mock ProjectLogService projectLogs;
  @Mock ProjectLogRepository projectLogRepo;
  @Mock SearchIndexPublisher searchIndex;
  @Mock ObjectProvider<BrowseRecorder> browseRecorder;
  @Mock TaskDialogMembership dialogMembership;
  @Mock TaskTemplateService taskTemplates;
  @Mock TaskContentService taskContents;
  @Mock ObjectProvider<TaskAiService> taskAi;
  @Mock ObjectProvider<RealtimeFanoutPublisher> realtimeFanout;
  @Mock TaskColumnFlowSync columnFlowSync;
  @Mock ObjectMapper objectMapper;
  TaskService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
    lenient().when(taskAi.getIfAvailable()).thenReturn(null);
    lenient().when(realtimeFanout.getIfAvailable()).thenReturn(null);
    // 多个 ObjectProvider 泛型擦除，不能依赖 @InjectMocks 按类型注入
    service =
        new TaskService(
            tasks,
            taskFiles,
            visibilityUsers,
            taskTags,
            access,
            projects,
            columns,
            flowItems,
            projectFlows,
            projectTags,
            projectPermissions,
            projectLogs,
            projectLogRepo,
            searchIndex,
            browseRecorder,
            null,
            dialogMembership,
            taskTemplates,
            taskContents,
            taskAi,
            realtimeFanout,
            columnFlowSync,
            objectMapper);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  private static TaskView upd(
      TaskService service,
      long taskId,
      Integer visibility,
      String visibilityUserIds,
      Integer complete,
      String owner,
      String assist,
      String tagIds) {
    return service.update(
        taskId,
        null,
        null,
        null,
        null,
        visibility,
        visibilityUserIds,
        complete,
        null,
        null,
        null,
        null,
        null,
        null,
        owner,
        assist,
        null,
        tagIds,
        null);
  }

  @Test
  void add_withoutColumn_usesFirst() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    when(columns.listByProject(10L)).thenReturn(List.of(col));
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(tasks.nextSort(10L, 20L)).thenReturn(0);

    TaskView view = service.add(10L, null, "Quick", null, null, null, null, null, null, null, null, null);
    assertEquals("Quick", view.name());
    assertEquals(20L, view.columnId());
  }

  @Test
  void add_ok() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(tasks.nextSort(10L, 20L)).thenReturn(0);

    TaskView view = service.add(10L, 20L, "T1", null, null, null, null, null, null, null, null, null);

    assertEquals("T1", view.name());
    assertEquals(10L, view.projectId());
    verify(tasks).insert(any(TaskItem.class));
    verify(tasks).insertAssignee(anyLong(), anyLong(), eq(0L), eq(10L), eq(1L), eq(1));
  }

  @Test
  void add_publishesTaskCreatedFanout() {
    RealtimeFanoutPublisher publisher = org.mockito.Mockito.mock(RealtimeFanoutPublisher.class);
    when(realtimeFanout.getIfAvailable()).thenReturn(publisher);
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(access.listMemberUserIds(10L)).thenReturn(List.of(1L, 2L));
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(tasks.nextSort(10L, 20L)).thenReturn(0);
    when(taskTags.listTagIds(anyLong())).thenReturn(List.of());

    service.add(10L, 20L, "T1", null, null, null, null, null, null, null, null, null);

    ArgumentCaptor<RealtimeFanoutEvent> cap = ArgumentCaptor.forClass(RealtimeFanoutEvent.class);
    verify(publisher).publish(cap.capture());
    assertEquals(RealtimeEventTypes.TASK_CREATED, cap.getValue().type());
    assertEquals(List.of(1L, 2L), cap.getValue().userIds());
  }

  @Test
  void add_withTemplateId_recordsUsage() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(10L);
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(tasks.nextSort(10L, 20L)).thenReturn(0);

    service.add(10L, 20L, "T1", null, null, null, null, null, null, null, null, 99L);
    verify(taskTemplates).recordUsage(99L, 10L);
  }

  @Test
  void add_badColumn() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setProjectId(99L);
    when(columns.findActive(20L)).thenReturn(Optional.of(col));

    assertThrows(
        BusinessException.class,
        () -> service.add(10L, 20L, "T1", null, null, null, null, null, null, null, null, null));
  }

  @Test
  void update_complete() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("T1");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(taskTags.listTagIds(5L)).thenReturn(List.of());

    TaskView view = upd(service, 5L, null, null, 1, null, null, null);
    assertNotNull(view.completeAt());
    verify(tasks).update(t);
  }

  @Test
  void update_owners() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(access.findOwner(10L, 2L)).thenReturn(Optional.of(0));
    when(access.findOwner(10L, 3L)).thenReturn(Optional.of(0));
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("T1");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(taskTags.listTagIds(5L)).thenReturn(List.of());

    upd(service, 5L, null, null, null, "2,3", null, null);

    verify(tasks).insertAssignee(anyLong(), eq(5L), eq(5L), eq(10L), eq(2L), eq(1));
    verify(tasks).insertAssignee(anyLong(), eq(5L), eq(5L), eq(10L), eq(3L), eq(1));
    verify(tasks).deleteAssigneesNotIn(eq(5L), eq(1), any());
  }

  @Test
  void update_assist_mainOnly() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(9L);
    t.setProjectId(10L);
    t.setName("子");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(tasks.findActive(9L)).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> upd(service, 5L, null, null, null, null, "2", null));
  }

  @Test
  void update_visibilityUsers() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(access.findOwner(10L, 8L)).thenReturn(Optional.of(0));
    when(access.findOwner(10L, 9L)).thenReturn(Optional.of(0));
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("T1");
    t.setVisibility(1);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(visibilityUsers.listUserIds(5L)).thenReturn(List.of(8L, 9L));
    when(taskTags.listTagIds(5L)).thenReturn(List.of());

    TaskView view = upd(service, 5L, 3, "8,9", null, null, null, null);

    assertEquals(3, view.visibility());
    assertEquals(List.of(8L, 9L), view.visibilityUserIds());
    verify(visibilityUsers).replace(eq(5L), eq(10L), any());
    verify(tasks).updateChildrenVisibility(5L, 3);
  }

  @Test
  void update_tagids() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setName("T1");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(projectTags.filterValidTagIds(eq(10L), any())).thenReturn(List.of(7L, 8L));
    when(taskTags.listTagIds(5L)).thenReturn(List.of(7L, 8L));

    TaskView view = upd(service, 5L, null, null, null, null, null, "7,8");
    assertEquals(List.of(7L, 8L), view.tagIds());
    verify(taskTags).replace(eq(5L), eq(10L), eq(List.of(7L, 8L)));
  }

  @Test
  void one_hiddenByVisibility() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setVisibility(2);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(tasks.isAssignee(5L, 1L)).thenReturn(false);

    assertThrows(BusinessException.class, () -> service.one(5L));
  }

  @Test
  void addSubtask_ok() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem parent = new TaskItem();
    parent.setId(5L);
    parent.setParentId(0L);
    parent.setProjectId(10L);
    parent.setColumnId(20L);
    parent.setVisibility(1);
    parent.setDialogId(0L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(parent));
    when(tasks.countChildren(5L)).thenReturn(0);
    when(taskTags.listTagIds(anyLong())).thenReturn(List.of());

    TaskView view = service.addSubtask(5L, "子任务", null, null);
    assertEquals("子任务", view.name());
    assertEquals(5L, view.parentId());
    verify(tasks).insert(any(TaskItem.class));
  }

  @Test
  void addSubtask_nestedRejected() {
    TaskItem parent = new TaskItem();
    parent.setId(5L);
    parent.setParentId(3L);
    parent.setProjectId(10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(parent));
    assertThrows(BusinessException.class, () -> service.addSubtask(5L, "x", null, null));
  }

  @Test
  void remove_cascades() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));

    service.remove(5L);
    verify(tasks).softDelete(5L, 1L);
    verify(tasks).softDeleteChildren(5L, 1L);
  }

  @Test
  void move_crossProject() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(access.requireMember(11L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("T1");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    ProjectColumn col = new ProjectColumn();
    col.setId(21L);
    col.setProjectId(11L);
    when(columns.findActive(21L)).thenReturn(Optional.of(col));
    when(tasks.nextSort(11L, 21L)).thenReturn(0);
    when(tasks.listByParent(5L)).thenReturn(List.of());
    when(taskTags.listTagIds(5L)).thenReturn(List.of());

    List<TaskView> out = service.move(5L, 11L, 21L, null);
    assertEquals(1, out.size());
    assertEquals(11L, out.get(0).projectId());
    verify(taskTags).deleteByTask(5L);
    verify(tasks).update(t);
    verify(tasks).moveChildrenLocation(5L, 11L, 21L);
  }

  @Test
  void move_subtaskRejected() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(9L);
    t.setProjectId(10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    assertThrows(BusinessException.class, () -> service.move(5L, 10L, 20L, null));
  }

  @Test
  void upgrade_ok() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(9L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("子");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    TaskItem parent = new TaskItem();
    parent.setId(9L);
    parent.setVisibility(1);
    parent.setPriorityLevel(2);
    parent.setPriorityName("重要");
    parent.setPriorityColor("#f00");
    when(tasks.findActive(9L)).thenReturn(Optional.of(parent));
    when(tasks.nextSort(10L, 20L)).thenReturn(3);
    when(taskTags.listTagIds(5L)).thenReturn(List.of());

    TaskView view = service.upgrade(5L);
    assertEquals(0L, view.parentId());
    assertEquals(2, view.priorityLevel());
    verify(tasks).updateAssigneeParentTaskId(5L, 5L);
  }

  @Test
  void upgrade_alreadyMain() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    assertThrows(BusinessException.class, () -> service.upgrade(5L));
  }

  @Test
  void copy_ok() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(access.requireMember(11L, 1L)).thenReturn(0);
    TaskItem src = new TaskItem();
    src.setId(5L);
    src.setParentId(0L);
    src.setProjectId(10L);
    src.setColumnId(20L);
    src.setName("源任务");
    src.setDescription("d");
    src.setVisibility(1);
    when(tasks.findActive(5L)).thenReturn(Optional.of(src));
    ProjectColumn col = new ProjectColumn();
    col.setId(21L);
    col.setProjectId(11L);
    when(columns.findActive(21L)).thenReturn(Optional.of(col));
    when(tasks.nextSort(11L, 21L)).thenReturn(2);
    when(tasks.listAssignees(5L)).thenReturn(List.of(new long[] {1L, 1}));
    when(tasks.listByParent(5L)).thenReturn(List.of());
    when(taskFiles.listByTask(5L)).thenReturn(List.of());
    when(taskTags.listTagIds(anyLong())).thenReturn(List.of());

    TaskView view = service.copy(5L, 11L, 21L, null, null);
    assertEquals("源任务", view.name());
    assertEquals(11L, view.projectId());
    assertEquals(21L, view.columnId());
    assertEquals(0L, view.parentId());
    verify(tasks).insert(any(TaskItem.class));
    verify(tasks).insertAssignee(anyLong(), anyLong(), eq(0L), eq(11L), eq(1L), eq(1));
  }

  @Test
  void copy_subRejected() {
    TaskItem src = new TaskItem();
    src.setId(5L);
    src.setParentId(9L);
    src.setProjectId(10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(src));
    assertThrows(BusinessException.class, () -> service.copy(5L, 10L, 20L, null, null));
  }

  @Test
  void easyLists_requiresUserIds() {
    assertThrows(BusinessException.class, () -> service.easyLists(null, null, null, null));
  }

  @Test
  void easyLists_ok() {
    when(tasks.listEasy(eq(List.of(1L, 2L)), any(), any(), eq(9L), eq(100)))
        .thenReturn(List.of(Map.of("id", 1L, "name", "A")));
    List<Map<String, Object>> out =
        service.easyLists("1,2", "2026-01-01 00:00:00,2026-01-31 23:59:59", 9L, null);
    assertEquals(1, out.size());
  }

  @Test
  void update_complete_spawnsRecurring() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("日报");
    t.setLoop(TaskRecurring.DAY);
    t.setStartAt(LocalDateTime.of(2026, 8, 5, 9, 0));
    t.setEndAt(LocalDateTime.of(2026, 8, 5, 18, 0));
    t.setLoopAt(t.getEndAt());
    t.setVisibility(1);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(taskTags.listTagIds(5L)).thenReturn(List.of(7L));
    Project p = new Project();
    p.setId(10L);
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    when(tasks.nextSort(10L, 20L)).thenReturn(3);
    when(tasks.listAssignees(5L)).thenReturn(List.of(new long[] {2L, 1}, new long[] {3L, 0}));
    when(access.requireMember(10L, 2L)).thenReturn(0);
    when(access.requireMember(10L, 3L)).thenReturn(0);

    TaskView view = upd(service, 5L, null, null, 1, null, null, null);
    assertNotNull(view.completeAt());
    ArgumentCaptor<TaskItem> inserted = ArgumentCaptor.forClass(TaskItem.class);
    verify(tasks).insert(inserted.capture());
    TaskItem next = inserted.getValue();
    assertEquals("日报", next.getName());
    assertEquals(TaskRecurring.DAY, next.getLoop());
    assertEquals(LocalDateTime.of(2026, 8, 6, 9, 0), next.getStartAt());
    assertEquals(LocalDateTime.of(2026, 8, 6, 18, 0), next.getEndAt());
    assertNull(next.getCompleteAt());
    verify(taskTags).replace(eq(next.getId()), eq(10L), eq(List.of(7L)));
  }

  @Test
  void update_complete_skipsRecurringWhenProjectArchived() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("日报");
    t.setLoop(TaskRecurring.WEEK);
    t.setEndAt(LocalDateTime.of(2026, 8, 5, 18, 0));
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(taskTags.listTagIds(5L)).thenReturn(List.of());
    Project p = new Project();
    p.setId(10L);
    p.setArchivedAt(LocalDateTime.now());
    when(projects.findActive(10L)).thenReturn(Optional.of(p));

    upd(service, 5L, null, null, 1, null, null, null);
    verify(tasks, never()).insert(any(TaskItem.class));
  }

  @Test
  void update_loopRequiresEndAt() {
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setName("无截止");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));

    assertThrows(
        BusinessException.class,
        () ->
            service.update(
                5L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                TaskRecurring.DAY));
  }
}
