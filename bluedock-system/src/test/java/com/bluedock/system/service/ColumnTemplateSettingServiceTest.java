package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class ColumnTemplateSettingServiceTest {
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;
  ObjectMapper json = new ObjectMapper();
  ColumnTemplateSettingService service;

  @BeforeEach
  void setUp() {
    service = new ColumnTemplateSettingService(settings, json, adminGuard, writeGuard);
  }

  @Test
  void get_defaultsWhenEmpty() {
    when(settings.findSettingJson("columnTemplate")).thenReturn(Optional.empty());
    List<Map<String, Object>> list = service.get();
    assertEquals(2, list.size());
    assertEquals("软件开发", list.get(0).get("name"));
  }

  @Test
  void save_parsesCommaColumns() {
    List<Map<String, Object>> out =
        service.save(List.of(Map.of("name", "研发", "columns", "设计,开发,测试,开发")));
    assertEquals(List.of("设计", "开发", "测试"), out.get(0).get("columns"));
    verify(writeGuard).requireWritable();
    verify(settings).upsert(eq("columnTemplate"), contains("研发"));
  }

  @Test
  void save_rejectsEmptyList() {
    assertThrows(BusinessException.class, () -> service.save(List.of()));
  }
}
