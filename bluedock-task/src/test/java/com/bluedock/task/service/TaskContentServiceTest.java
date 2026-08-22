package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskContent;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskContentRepository;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.web.dto.TaskContentDtos.TaskContentHistoryPage;
import com.bluedock.task.web.dto.TaskContentDtos.TaskContentView;
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
class TaskContentServiceTest {
  @Mock TaskRepository tasks;
  @Mock TaskContentRepository contents;
  @Mock ProjectAccessService access;
  @InjectMocks TaskContentService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void generateDescription_stripsHtml() {
    assertEquals("hello world", TaskContentService.generateDescription("<p>hello <b>world</b></p>"));
  }

  @Test
  void get_emptyReturnsMap() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(10L);
    t.setName("T");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(contents.findLatest(5L)).thenReturn(Optional.empty());

    Object out = service.get(5L, null);
    assertInstanceOf(Map.class, out);
    assertTrue(((Map<?, ?>) out).isEmpty());
  }

  @Test
  void get_latest() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(10L);
    t.setName("T");
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    TaskContent c = new TaskContent();
    c.setId(9L);
    c.setTaskId(5L);
    c.setProjectId(10L);
    c.setContent("<p>x</p>");
    c.setDescription("x");
    when(contents.findLatest(5L)).thenReturn(Optional.of(c));

    Object out = service.get(5L, null);
    assertInstanceOf(TaskContentView.class, out);
    assertEquals("<p>x</p>", ((TaskContentView) out).content());
  }

  @Test
  void history_ok() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(10L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(access.requireMember(10L, 1L)).thenReturn(0);
    when(contents.countByTask(5L)).thenReturn(1L);
    TaskContent c = new TaskContent();
    c.setId(9L);
    c.setTaskId(5L);
    c.setDescription("摘要");
    when(contents.listHistory(5L, 0, 20)).thenReturn(List.of(c));

    TaskContentHistoryPage page = service.history(5L, 1, 20);
    assertEquals(1, page.items().size());
    assertEquals(1L, page.meta().totalSize());
  }

  @Test
  void save_rejectsSubtask() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(1L);
    assertThrows(BusinessException.class, () -> service.save(t, "<p>a</p>", 1L));
  }

  @Test
  void save_ok() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setParentId(0L);
    t.setProjectId(10L);
    String summary = service.save(t, "<p>hello</p>", 1L);
    assertEquals("hello", summary);
    verify(contents).insert(any(TaskContent.class));
  }
}
