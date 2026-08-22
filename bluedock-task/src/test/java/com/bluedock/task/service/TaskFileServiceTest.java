package com.bluedock.task.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.project.service.ProjectAccessService;
import com.bluedock.task.domain.TaskFile;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskFileRepository;
import com.bluedock.task.repo.TaskRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class TaskFileServiceTest {
  @Mock TaskFileRepository files;
  @Mock TaskRepository tasks;
  @Mock ProjectAccessService access;
  @Mock ObjectProvider<BrowseRecorder> browseRecorder;
  @Mock BrowseRecorder recorder;
  TaskFileService service;

  @BeforeEach
  void setUp() {
    service = new TaskFileService(files, tasks, access, browseRecorder);
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void detail_recordsRecent() {
    TaskFile f = new TaskFile();
    f.setId(9L);
    f.setTaskId(5L);
    f.setProjectId(3L);
    f.setName("a.pdf");
    when(files.findActive(9L)).thenReturn(Optional.of(f));
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(3L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(access.requireMember(3L, 1L)).thenReturn(0);
    when(browseRecorder.getIfAvailable()).thenReturn(recorder);

    Map<String, Object> out = service.detail(9L, "no");
    assertEquals(9L, out.get("id"));
    verify(recorder).recordTaskFile(1L, 9L, 5L);
  }

  @Test
  void attach_ok() {
    TaskItem t = new TaskItem();
    t.setId(5L);
    t.setProjectId(3L);
    t.setParentId(0L);
    when(tasks.findActive(5L)).thenReturn(Optional.of(t));
    when(access.requireMember(3L, 1L)).thenReturn(0);

    var view = service.attach(5L, "doc.txt", 12, "txt", "/p/doc.txt", "");
    assertEquals("doc.txt", view.name());
    verify(files).insert(any(TaskFile.class));
  }
}
