package com.bluedock.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.ai.OpenAiCompatibleChatClient;
import com.bluedock.system.service.AiBotSettingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiBotChatServiceTest {
  @Mock AiBotSettingService settings;
  @Mock OpenAiCompatibleChatClient client;

  @Test
  void resolveCred_openaiDefaults() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("open", "open");
    raw.put("openaiKey", "sk-oai");
    when(settings.loadRaw()).thenReturn(raw);
    AiBotChatService service = new AiBotChatService(settings, client);
    AiBotChatService.Cred cred = service.resolveCred(null);
    assertNotNull(cred);
    assertEquals("sk-oai", cred.apiKey());
    assertEquals("https://api.openai.com", cred.baseUrl());
    assertEquals("gpt-4o-mini", cred.model());
    assertTrue(service.available());
  }

  @Test
  void resolveCred_closed() {
    when(settings.loadRaw()).thenReturn(Map.of("open", "close", "openaiKey", "sk"));
    AiBotChatService service = new AiBotChatService(settings, client);
    assertNull(service.resolveCred(null));
    assertFalse(service.available());
  }

  @Test
  void chat_ok() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("open", "open");
    raw.put("apiKey", "k");
    raw.put("baseUrl", "https://gw.example");
    raw.put("model", "m1");
    when(settings.loadRaw()).thenReturn(raw);
    when(client.chatCompletions(eq("https://gw.example"), eq("k"), eq("m1"), anyList()))
        .thenReturn("out");
    AiBotChatService service = new AiBotChatService(settings, client);
    assertEquals("out", service.chat("sys", "user"));
  }

  @Test
  void firstModelId_fromList() {
    assertEquals(
        "deepseek-chat",
        AiBotChatService.firstModelId(List.of(Map.of("id", "deepseek-chat")), "x"));
    assertEquals("x", AiBotChatService.firstModelId(List.of(), "x"));
  }

  @Test
  void reportBridge_polish() {
    when(settings.loadRaw())
        .thenReturn(Map.of("open", "open", "openaiKey", "sk", "model", "gpt-4o-mini"));
    when(client.chatCompletions(anyString(), anyString(), anyString(), anyList()))
        .thenReturn("## 今日\n- A");
    AiBotChatService chat = new AiBotChatService(settings, client);
    SystemReportAiDraftBridge bridge = new SystemReportAiDraftBridge(chat);
    assertTrue(bridge.available());
    assertEquals("## 今日\n- A", bridge.polish("daily", "完成了 A"));
    verify(client).chatCompletions(anyString(), anyString(), anyString(), anyList());
  }
}
