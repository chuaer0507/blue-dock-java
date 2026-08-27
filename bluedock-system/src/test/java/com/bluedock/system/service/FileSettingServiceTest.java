package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.system.repo.SettingRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSettingServiceTest {
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;
  ObjectMapper json = new ObjectMapper();
  FileSettingService service;

  @BeforeEach
  void setUp() {
    service = new FileSettingService(settings, json, adminGuard, writeGuard);
  }

  @Test
  void get_defaults() {
    when(settings.findSettingJson("fileSetting")).thenReturn(Optional.empty());
    Map<String, Object> view = service.get();
    assertEquals("all", view.get("packPermission"));
    verify(adminGuard).requireAdmin();
  }

  @Test
  void save_ok() {
    when(settings.findSettingJson("fileSetting")).thenReturn(Optional.empty());
    Map<String, Object> out = service.save(Map.of("uploadMaxMb", "512"));
    assertEquals("512", out.get("uploadMaxMb"));
    verify(writeGuard).requireWritable();
    verify(settings).upsert(eq("fileSetting"), contains("uploadMaxMb"));
  }
}
