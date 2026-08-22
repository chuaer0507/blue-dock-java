package com.bluedock.task.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.system.service.SystemGeneralSettingService;
import com.bluedock.task.repo.TaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskAutoArchiveServiceTest {
  @Mock private TaskRepository tasks;
  @Mock private SystemGeneralSettingService settings;
  @InjectMocks private TaskAutoArchiveService service;

  @Test
  void archivesWhenSystemOpenAndPastDays() {
    when(settings.loadRaw()).thenReturn(Map.of("autoArchive", "open", "autoArchiveDay", 7));
    LocalDateTime now = LocalDateTime.of(2026, 8, 6, 12, 0);
    when(tasks.listAutoArchiveCandidates(any(), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    1L,
                    "completeAt",
                    now.minusDays(10),
                    "projectId",
                    9L,
                    "archiveMethod",
                    "system",
                    "archiveDays",
                    30)));

    Map<String, Object> out = service.runAt(now);
    assertEquals(1, out.get("archived"));
    verify(tasks).archive(1L, 0L);
    verify(tasks).archiveChildren(1L, 0L);
  }

  @Test
  void skipsSystemWhenClosed() {
    when(settings.loadRaw()).thenReturn(Map.of("autoArchive", "close", "autoArchiveDay", 7));
    LocalDateTime now = LocalDateTime.of(2026, 8, 6, 12, 0);
    when(tasks.listAutoArchiveCandidates(any(), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    1L,
                    "completeAt",
                    now.minusDays(10),
                    "projectId",
                    9L,
                    "archiveMethod",
                    "system",
                    "archiveDays",
                    30)));

    Map<String, Object> out = service.runAt(now);
    assertEquals(0, out.get("archived"));
    verify(tasks, never()).archive(anyLong(), anyLong());
  }

  @Test
  void customProjectIgnoresSystemSwitch() {
    when(settings.loadRaw()).thenReturn(Map.of("autoArchive", "close", "autoArchiveDay", 7));
    LocalDateTime now = LocalDateTime.of(2026, 8, 6, 12, 0);
    when(tasks.listAutoArchiveCandidates(any(), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    2L,
                    "completeAt",
                    now.minusDays(5),
                    "projectId",
                    9L,
                    "archiveMethod",
                    "custom",
                    "archiveDays",
                    3)));

    Map<String, Object> out = service.runAt(now);
    assertEquals(1, out.get("archived"));
    verify(tasks).archive(eq(2L), eq(0L));
  }
}
