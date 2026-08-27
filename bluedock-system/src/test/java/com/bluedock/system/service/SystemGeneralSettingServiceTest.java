package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.system.repo.SettingRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemGeneralSettingServiceTest {
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;
  ObjectMapper json = new ObjectMapper();
  SystemGeneralSettingService service;

  @BeforeEach
  void setUp() {
    service = new SystemGeneralSettingService(settings, json, adminGuard, writeGuard);
  }

  @Test
  void get_mergesDefaults() {
    when(settings.findSettingJson("systemSetting")).thenReturn(Optional.empty());
    when(writeGuard.isDisabled()).thenReturn(false);
    Map<String, Object> view = service.get();
    assertEquals("simple", view.get("passwordType"));
    assertEquals(true, view.get("writable"));
    verify(adminGuard).requireAdmin();
  }

  @Test
  void save_rejectsBadArchiveDay() {
    when(settings.findSettingJson("systemSetting")).thenReturn(Optional.empty());
    assertThrows(
        BusinessException.class, () -> service.save(Map.of("autoArchiveDay", 999)));
  }

  @Test
  void save_ok() throws Exception {
    when(settings.findSettingJson("systemSetting")).thenReturn(Optional.empty());
    Map<String, Object> out = service.save(Map.of("messageRecallLimit", 30));
    assertEquals(30, out.get("messageRecallLimit"));
    verify(writeGuard).requireWritable();
    verify(settings).upsert(eq("systemSetting"), org.mockito.ArgumentMatchers.contains("messageRecallLimit"));
  }
}
