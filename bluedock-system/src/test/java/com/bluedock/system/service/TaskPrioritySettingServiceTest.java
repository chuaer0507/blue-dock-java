package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.system.repo.SettingRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskPrioritySettingServiceTest {
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;
  ObjectMapper json = new ObjectMapper();
  TaskPrioritySettingService service;

  @BeforeEach
  void setUp() {
    service = new TaskPrioritySettingService(settings, json, adminGuard, writeGuard);
  }

  @Test
  void get_defaultsWhenEmpty() {
    when(settings.findSettingJson("priority")).thenReturn(Optional.empty());
    List<Map<String, Object>> list = service.get();
    assertEquals(4, list.size());
    assertEquals(1, list.get(0).get("isDefault"));
  }

  @Test
  void save_rejectsEmpty() {
    assertThrows(BusinessException.class, () -> service.save(List.of()));
  }

  @Test
  void save_normalizesSingleDefault() {
    List<Map<String, Object>> out =
        service.save(
            List.of(
                Map.of("name", "A", "color", "#111", "days", 1, "priority", 1, "isDefault", 1),
                Map.of("name", "B", "color", "#222", "days", 2, "priority", 2, "is_default", 1)));
    assertEquals(1, out.get(0).get("isDefault"));
    assertEquals(0, out.get(1).get("isDefault"));
    verify(writeGuard).requireWritable();
    verify(settings).upsert(eq("priority"), contains("\"name\":\"A\""));
  }

  @Test
  void normalize_skipsInvalid() {
    assertTrue(
        TaskPrioritySettingService.normalize(
                List.of(Map.of("name", "", "color", "#fff", "priority", 1)))
            .isEmpty());
  }
}
