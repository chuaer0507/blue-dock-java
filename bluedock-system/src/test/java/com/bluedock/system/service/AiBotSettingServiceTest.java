package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AiBotSettingServiceTest {
  @Mock SettingRepository settings;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;
  ObjectMapper json = new ObjectMapper();
  AiBotSettingService service;

  @BeforeEach
  void setUp() {
    service = new AiBotSettingService(settings, json, adminGuard, writeGuard);
  }

  @Test
  void get_defaults_masksEmptyKeysAsEmpty() {
    when(settings.findSettingJson("aiBotSetting")).thenReturn(Optional.empty());
    Map<String, Object> view = service.get();
    assertEquals("close", view.get("open"));
    assertEquals("", view.get("apiKey"));
    verify(adminGuard).requireAdmin();
  }

  @Test
  void get_masksConfiguredKeys() {
    when(settings.findSettingJson("aiBotSetting"))
        .thenReturn(
            Optional.of(
                "{\"open\":\"open\",\"apiKey\":\"sk-live\",\"openaiKey\":\"ok-1\",\"openaiModels\":[\"a\"]}"));
    Map<String, Object> view = service.get();
    assertEquals(AiBotSettingService.SECRET_MASK, view.get("apiKey"));
    assertEquals(AiBotSettingService.SECRET_MASK, view.get("openaiKey"));
    assertEquals(List.of("a"), view.get("openaiModels"));
  }

  @Test
  void get_migratesLegacySnakeKeys() {
    when(settings.findSettingJson("aiBotSetting"))
        .thenReturn(Optional.of("{\"openai_key\":\"ok-legacy\",\"openai_models\":[\"m\"]}"));
    Map<String, Object> view = service.get();
    assertEquals(AiBotSettingService.SECRET_MASK, view.get("openaiKey"));
    assertEquals(List.of("m"), view.get("openaiModels"));
    assertFalse(view.containsKey("openai_key"));
  }

  @Test
  void save_and_models() {
    when(settings.findSettingJson("aiBotSetting")).thenReturn(Optional.empty());
    service.save(Map.of("open", "open", "models", List.of(Map.of("id", "m1", "name", "M1"))));
    verify(writeGuard).requireWritable();
    verify(settings).upsert(eq("aiBotSetting"), contains("\"open\":\"open\""));

    when(settings.findSettingJson("aiBotSetting"))
        .thenReturn(Optional.of("{\"open\":\"open\",\"models\":[{\"id\":\"m1\",\"name\":\"M1\"}]}"));
    List<Map<String, Object>> models = service.models();
    assertEquals(1, models.size());
    assertEquals("m1", models.get(0).get("id"));
  }

  @Test
  void mergeIncoming_keepsSecretsWhenMasked() {
    Map<String, Object> current = AiBotSettingService.defaults();
    current.put("openaiKey", "sk-real");
    current.put("apiKey", "legacy");
    Map<String, Object> merged =
        AiBotSettingService.mergeIncoming(
            current,
            Map.of(
                "openaiKey",
                AiBotSettingService.SECRET_MASK,
                "apiKey",
                "",
                "open",
                "open"));
    assertEquals("sk-real", merged.get("openaiKey"));
    assertEquals("legacy", merged.get("apiKey"));
    assertEquals("open", merged.get("open"));
  }

  @Test
  void isSecretField() {
    assertTrue(AiBotSettingService.isSecretField("apiKey"));
    assertTrue(AiBotSettingService.isSecretField("openaiKey"));
    assertTrue(AiBotSettingService.isSecretField("apiSecret"));
    assertFalse(AiBotSettingService.isSecretField("appCertificate"));
    assertFalse(AiBotSettingService.isSecretField("openaiModels"));
    assertFalse(AiBotSettingService.isSecretField("model"));
  }

  @Test
  void defModels_notEmpty() {
    List<Map<String, Object>> defs = service.defModels();
    assertFalse(defs.isEmpty());
    verify(adminGuard).requireAdmin();
  }
}
