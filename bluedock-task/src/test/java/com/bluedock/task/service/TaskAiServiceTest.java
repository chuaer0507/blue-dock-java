package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.project.TaskAiDialogBridge;
import com.bluedock.project.domain.Project;
import com.bluedock.task.dialog.TaskDialogMembership;
import com.bluedock.project.domain.ProjectColumn;
import com.bluedock.project.repo.ProjectColumnRepository;
import com.bluedock.project.repo.ProjectRepository;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.project.service.ProjectLogService;
import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.domain.TaskAiEvent;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskAiEventRepository;
import com.bluedock.task.repo.TaskContentRepository;
import com.bluedock.task.repo.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskAiServiceTest {
  @Mock
  TaskRepository tasks;
  @Mock
  TaskAiEventRepository aiEvents;
  @Mock
  TaskContentRepository contents;
  @Mock
  ProjectRepository projects;
  @Mock
  ProjectColumnRepository columns;
  @Mock
  ProjectAccessService access;
  @Mock
  ProjectLogService projectLogs;
  @Mock
  TaskRelationService relations;
  @Mock
  SystemGeneralSettingService systemSettings;
  @Mock
  TaskAiDialogBridge dialogBridge;
  @Mock
  TaskDialogMembership dialogMembership;
  @Mock
  com.bluedock.system.ai.AiBotChatService aiBotChat;
  @InjectMocks
  TaskAiService service;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AtomicLong ids = new AtomicLong(1000);

  @BeforeEach
  void setUp() throws Exception {
    AuthContext.set(new AuthUser(1L));
    var field = TaskAiService.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(service, objectMapper);
    when(systemSettings.isTaskAiAutoAnalyzeOpen()).thenReturn(true);
    when(aiBotChat.available()).thenReturn(false);
    when(dialogBridge.publishSuggestion(anyLong(), anyString(), anyLong(), any(), anyString()))
        .thenReturn(777L);
    when(dialogMembership.resolveMembers(any(TaskItem.class))).thenReturn(new java.util.HashSet<>(List.of(1L)));
    org.mockito.Mockito.doNothing().when(aiEvents).updateMessageIdForCompleted(anyLong(), anyLong());
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void generate_descriptionAndSubtasks() throws Exception {
    TaskItem t = mainTask();
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    Project p = new Project();
    p.setId(10L);
    p.setName("Demo");
    p.setAiAutoAnalyze("open");
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setName("Todo");
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(contents.findLatest(50L)).thenReturn(Optional.empty());
    when(tasks.countChildren(50L)).thenReturn(0);
    when(tasks.listAssignees(50L)).thenReturn(List.of(new long[] { 1L, 1 }));
    when(tasks.listAssigneeUserIds(50L)).thenReturn(List.of(1L));
    when(aiEvents.findSimilarByName(eq(10L), eq(50L), anyString(), anyInt())).thenReturn(List.of());
    java.util.Map<String, TaskAiEvent> store = new java.util.HashMap<>();
    for (String type : TaskAiEvent.eventTypes()) {
      store.put(type, pending(type));
    }
    when(aiEvents.findByTaskAndType(eq(50L), anyString()))
        .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(1))));
    when(aiEvents.markProcessing(anyLong())).thenReturn(true);
    org.mockito.Mockito.doAnswer(
        inv -> {
          long id = inv.getArgument(0);
          String json = inv.getArgument(1);
          long messageId = inv.getArgument(2);
          store.values().stream()
              .filter(e -> e.getId() == id)
              .findFirst()
              .ifPresent(
                  e -> {
                    e.setStatus(TaskAiEvent.STATUS_COMPLETED);
                    e.setResultJson(json);
                    e.setMessageId(messageId);
                  });
          return null;
        })
        .when(aiEvents)
        .markCompleted(anyLong(), anyString(), anyLong());
    org.mockito.Mockito.doAnswer(
        inv -> {
          long id = inv.getArgument(0);
          String reason = inv.getArgument(1);
          store.values().stream()
              .filter(e -> e.getId() == id)
              .findFirst()
              .ifPresent(
                  e -> {
                    e.setStatus(TaskAiEvent.STATUS_SKIPPED);
                    e.setError(reason);
                  });
          return null;
        })
        .when(aiEvents)
        .markSkipped(anyLong(), anyString());

    Map<String, Object> out = service.generate(50L);
    assertEquals(50L, out.get("taskId"));
    assertEquals(777L, out.get("messageId"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> suggestions = (List<Map<String, Object>>) out.get("suggestions");
    assertTrue(suggestions.stream().anyMatch(s -> "description".equals(s.get("type"))));
    assertTrue(suggestions.stream().anyMatch(s -> "subtasks".equals(s.get("type"))));
    verify(aiEvents, never()).insert(any());
    verify(dialogBridge).publishSuggestion(eq(50L), anyString(), eq(1L), any(), anyString());
    verify(aiEvents).updateMessageIdForCompleted(50L, 777L);
  }

  @Test
  void apply_similar_linksRelation() {
    TaskItem t = mainTask();
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskAiEvent e = pending(TaskAiEvent.TYPE_SIMILAR);
    e.setMessageId(99L);
    e.setStatus(TaskAiEvent.STATUS_COMPLETED);
    e.setResultJson("{\"type\":\"similar\",\"content\":[{\"taskId\":88}]}");
    when(aiEvents.findByTaskTypeMessage(50L, "similar", 99L)).thenReturn(Optional.of(e));
    when(relations.link(50L, 88L, 0L, 99L, 1L)).thenReturn(true);
    when(dialogBridge.updateActionStatus(0L, 99L, "similar", TaskAiEvent.STATUS_APPLIED, 0L, 88L))
        .thenReturn(Map.of("id", 99L));

    Map<String, Object> out = service.apply(50L, 99L, "similar", null, 88L);
    assertEquals("similar", out.get("type"));
    assertEquals(99L, ((Map<?, ?>) out.get("message")).get("id"));
    verify(aiEvents).markStatus(e.getId(), TaskAiEvent.STATUS_APPLIED);
    verify(relations).link(50L, 88L, 0L, 99L, 1L);
  }

  @Test
  void dismiss_marksDismissed() {
    TaskItem t = mainTask();
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskAiEvent e = pending(TaskAiEvent.TYPE_DESCRIPTION);
    e.setMessageId(99L);
    when(aiEvents.findByTaskTypeMessage(50L, "description", 99L)).thenReturn(Optional.of(e));

    Map<String, Object> out = service.dismiss(50L, 99L, "description", null, null);
    assertEquals("description", out.get("type"));
    verify(aiEvents).markStatus(e.getId(), TaskAiEvent.STATUS_DISMISSED);
  }

  @Test
  void apply_typeInvalid() {
    assertThrows(BusinessException.class, () -> service.apply(1L, 1L, "nope", null, null));
  }

  @Test
  void generate_createsPendingWhenMissing() {
    TaskItem t = mainTask();
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    Project p = new Project();
    p.setId(10L);
    p.setName("Demo");
    p.setAiAutoAnalyze("open");
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    when(columns.findActive(20L)).thenReturn(Optional.empty());
    when(contents.findLatest(50L)).thenReturn(Optional.empty());
    when(tasks.countChildren(50L)).thenReturn(0);
    when(tasks.listAssignees(50L)).thenReturn(List.of(new long[] { 1L, 1 }));
    when(aiEvents.findSimilarByName(anyLong(), anyLong(), anyString(), anyInt()))
        .thenReturn(List.of());
    java.util.Map<String, TaskAiEvent> store = new java.util.HashMap<>();
    when(aiEvents.findByTaskAndType(eq(50L), anyString()))
        .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(1))));
    org.mockito.Mockito.doAnswer(
        inv -> {
          TaskAiEvent e = inv.getArgument(0);
          store.put(e.getEventType(), e);
          return null;
        })
        .when(aiEvents)
        .insert(any());
    when(aiEvents.markProcessing(anyLong())).thenReturn(true);

    service.generate(50L);
    assertEquals(4, store.size());
  }

  @Test
  void buildCardMarkdown_containsAiActionMarkers() {
    List<Map<String, Object>> suggestions = List.of(
        Map.of("type", "description", "content", "目标"),
        Map.of("type", "subtasks", "content", List.of("a", "b")));
    String md = TaskAiService.buildCardMarkdown(50L, suggestions);
    assertTrue(md.contains(":::ai-action{type=description task_id=50 message_id=0}:::"));
    assertTrue(md.contains(":::ai-action{type=subtasks task_id=50 message_id=0}:::"));
    assertTrue(md.contains("- a"));
  }

  @Test
  void parseLlmSuggestion_descriptionAndFence() {
    Map<String, Object> m =
        service.parseLlmSuggestion(
            "```json\n{\"type\":\"description\",\"content\":\"## 目标\\n完成登录\"}\n```",
            TaskAiEvent.TYPE_DESCRIPTION);
    assertEquals("description", m.get("type"));
    assertEquals("llm", m.get("source"));
    assertTrue(String.valueOf(m.get("content")).contains("完成登录"));
  }

  @Test
  void parseLlmSuggestion_subtasks() {
    Map<String, Object> m =
        service.parseLlmSuggestion(
            "{\"type\":\"subtasks\",\"content\":[\"澄清\",\"实现\",\"自测\"]}",
            TaskAiEvent.TYPE_SUBTASKS);
    assertEquals(List.of("澄清", "实现", "自测"), m.get("content"));
  }

  @Test
  void generate_usesLlmWhenAvailable() throws Exception {
    when(aiBotChat.available()).thenReturn(true);
    when(aiBotChat.chat(anyString(), anyString()))
        .thenAnswer(
            inv -> {
              String user = inv.getArgument(1);
              if (user.contains("description")) {
                return "{\"type\":\"description\",\"content\":\"LLM 描述\"}";
              }
              if (user.contains("subtasks")) {
                return "{\"type\":\"subtasks\",\"content\":[\"一步\",\"二步\"]}";
              }
              return null;
            });
    TaskItem t = mainTask();
    when(tasks.findActive(50L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    Project p = new Project();
    p.setId(10L);
    p.setName("Demo");
    p.setAiAutoAnalyze("open");
    when(projects.findActive(10L)).thenReturn(Optional.of(p));
    ProjectColumn col = new ProjectColumn();
    col.setId(20L);
    col.setName("Todo");
    when(columns.findActive(20L)).thenReturn(Optional.of(col));
    when(contents.findLatest(50L)).thenReturn(Optional.empty());
    when(tasks.countChildren(50L)).thenReturn(0);
    when(tasks.listAssignees(50L)).thenReturn(List.of(new long[] {1L, 1}));
    when(tasks.listAssigneeUserIds(50L)).thenReturn(List.of(1L));
    when(aiEvents.findSimilarByName(eq(10L), eq(50L), anyString(), anyInt())).thenReturn(List.of());
    java.util.Map<String, TaskAiEvent> store = new java.util.HashMap<>();
    for (String type : TaskAiEvent.eventTypes()) {
      store.put(type, pending(type));
    }
    when(aiEvents.findByTaskAndType(eq(50L), anyString()))
        .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(1))));
    when(aiEvents.markProcessing(anyLong())).thenReturn(true);
    org.mockito.Mockito.doAnswer(
            inv -> {
              Long id = inv.getArgument(0);
              String json = inv.getArgument(1);
              for (TaskAiEvent e : store.values()) {
                if (e.getId() == id) {
                  e.setStatus(TaskAiEvent.STATUS_COMPLETED);
                  e.setResultJson(json);
                }
              }
              return null;
            })
        .when(aiEvents)
        .markCompleted(anyLong(), anyString(), anyLong());
    org.mockito.Mockito.doAnswer(
            inv -> {
              Long id = inv.getArgument(0);
              for (TaskAiEvent e : store.values()) {
                if (e.getId() == id) {
                  e.setStatus(TaskAiEvent.STATUS_SKIPPED);
                }
              }
              return null;
            })
        .when(aiEvents)
        .markSkipped(anyLong(), anyString());

    Map<String, Object> out = service.generate(50L);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> suggestions = (List<Map<String, Object>>) out.get("suggestions");
    assertTrue(
        suggestions.stream()
            .anyMatch(
                s ->
                    "description".equals(s.get("type"))
                        && "LLM 描述".equals(s.get("content"))
                        && "llm".equals(s.get("source"))));
  }

  private TaskItem mainTask() {
    TaskItem t = new TaskItem();
    t.setId(50L);
    t.setParentId(0L);
    t.setProjectId(10L);
    t.setColumnId(20L);
    t.setDialogId(0L);
    t.setName("实现登录与权限校验模块");
    t.setDescription("");
    t.setUserId(1L);
    return t;
  }

  private TaskAiEvent pending(String type) {
    TaskAiEvent e = new TaskAiEvent();
    e.setId(ids.incrementAndGet());
    e.setTaskId(50L);
    e.setEventType(type);
    e.setStatus(TaskAiEvent.STATUS_PENDING);
    e.setRetryCount(0);
    e.setMessageId(0L);
    return e;
  }
}
