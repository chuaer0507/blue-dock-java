package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.project.domain.ProjectFlowItem;
import com.bluedock.project.domain.ProjectLog;
import com.bluedock.project.permission.ProjectPermissionCodes;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectFlowRepository;
import com.bluedock.project.repo.ProjectLogRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectFlowService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.project.service.ProjectPermissionService;
import com.bluedock.project.service.ProjectTagService;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskFileRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskTagRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import com.bluedock.task.web.dto.TaskView;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskResetFromLogTest {
  @Mock
  TaskRepository tasks;
  @Mock
  TaskFileRepository taskFiles;
  @Mock
  TaskVisibilityUserRepository visibilityUsers;
  @Mock
  TaskTagRepository taskTags;
  @Mock
  ProjectAccessService access;
  @Mock
  ProjectRepository projects;
  @Mock
  ProjectColumnRepository columns;
  @Mock
  ProjectFlowRepository flowItems;
  @Mock
  ProjectFlowService projectFlows;
  @Mock
  ProjectTagService projectTags;
  @Mock
  ProjectPermissionService projectPermissions;
  @Mock
  ProjectLogService projectLogs;
  @Mock
  ProjectLogRepository projectLogRepo;
  @Mock
  SearchIndexPublisher searchIndex;
  @Mock
  ObjectProvider<BrowseRecorder> browseRecorder;
  @Mock
  TaskTemplateService taskTemplates;
  @Mock
  TaskContentService taskContents;
  @Mock
  ObjectProvider<TaskAiService> taskAi;
  @InjectMocks
  TaskService service;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    AuthContext.set(new AuthUser(1L));
    var f = TaskService.class.getDeclaredField("objectMapper");
    f.setAccessible(true);
    f.set(service, objectMapper);
    when(taskAi.getIfAvailable()).thenReturn(null);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void resetFromLog_restoresFlowAndComplete() {
    ProjectLog log = new ProjectLog();
    log.setId(900L);
    log.setTaskId(50L);
    log.setRecordJson(
        "{\"flow\":{\"flowItemId\":11,\"flowItemName\":\"开始\",\"completeAt\":null},\"change\":[\"开始\",\"完成\"]}");
    when(projectLogRepo.findById(900L)).thenReturn(Optional.of(log));

    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("任务A");
    t.setFlowItemId(22L);
    t.setFlowItemName("完成");
    t.setCompleteAt(LocalDateTime.now());
    t.setVisibility(1);
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);

    ProjectFlowItem start = new ProjectFlowItem();
    start.setId(11L);
    start.setName("开始");
    start.setStatus("start");
    start.setColumnId(0L);
    when(projectFlows.requireItemInProject(11L, 10L)).thenReturn(start);

    TaskView view = service.resetFromLog(900L);
    assertEquals(11L, t.getFlowItemId());
    assertEquals("开始", t.getFlowItemName());
    assertNull(t.getCompleteAt());
    assertEquals(50L, view.id());
    verify(tasks).update(t);
    verify(projectPermissions).require(10L, 1L, ProjectPermissionCodes.TASK_STATUS, 50L);
    verify(projectLogs)
        .recordTask(
            eq(10L),
            eq(20L),
            eq(50L),
            eq(0L),
            eq("任务A"),
            eq("重置{任务}状态"),
            any(),
            eq(0));
  }

  @Test
  void resetFromLog_unsupportedWithoutFlow() {
    ProjectLog log = new ProjectLog();
    log.setId(901L);
    log.setTaskId(50L);
    log.setRecordJson("{\"change\":[\"a\",\"b\"]}");
    when(projectLogRepo.findById(901L)).thenReturn(Optional.of(log));
    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setProjectId(10L);
    t.setVisibility(1);
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);

    assertThrows(BusinessException.class, () -> service.resetFromLog(901L));
  }

  @Test
  void applyFlowItemWithLog_writesFlowSnapshot() {
    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setName("任务A");
    t.setFlowItemId(11L);
    t.setFlowItemName("开始");
    t.setVisibility(1);
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);

    ProjectFlowItem cur = new ProjectFlowItem();
    cur.setId(11L);
    cur.setName("开始");
    cur.setTurns("22");
    cur.setStatus("start");
    when(flowItems.findActiveItem(11L)).thenReturn(Optional.of(cur));

    ProjectFlowItem end = new ProjectFlowItem();
    end.setId(22L);
    end.setName("完成");
    end.setStatus("end");
    end.setColumnId(0L);
    when(projectFlows.requireItemInProject(22L, 10L)).thenReturn(end);

    service.flow(50L, 22L);
    verify(projectLogs)
        .recordTask(
            eq(10L),
            eq(20L),
            eq(50L),
            eq(0L),
            eq("任务A"),
            eq("修改{任务}状态"),
            anyMap(),
            eq(0));
    assertEquals(22L, t.getFlowItemId());
  }
}
