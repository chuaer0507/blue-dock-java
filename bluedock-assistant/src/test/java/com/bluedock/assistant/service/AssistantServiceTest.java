package com.bluedock.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.assistant.domain.AssistantSession;
import com.bluedock.assistant.repo.AssistantFeedbackRepository;
import com.bluedock.assistant.repo.AssistantSearchLogRepository;
import com.bluedock.assistant.repo.AssistantSessionRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.repo.SettingRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {
  @Mock AssistantSessionRepository sessions;
  @Mock AssistantFeedbackRepository feedbacks;
  @Mock AssistantSearchLogRepository searchLogs;
  @Mock SettingRepository settings;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> valueOps;
  @Mock SetOperations<String, String> setOps;
  @Mock RealtimeFanoutPublisher fanout;
  @Mock ObjectProvider<ObjectStorage> objectStorage;
  @Mock ObjectStorage storage;
  @Mock ObjectProvider<com.bluedock.system.ai.AiBotChatService> aiChat;

  AssistantService service;

  @BeforeEach
  void setUp() {
    lenient().when(objectStorage.getIfAvailable()).thenReturn(storage);
    lenient().when(aiChat.getIfAvailable()).thenReturn(null);
    service =
        new AssistantService(
            sessions,
            feedbacks,
            searchLogs,
            settings,
            redis,
            fanout,
            new ObjectMapper(),
            objectStorage,
            aiChat);
    AuthContext.set(new AuthUser(7L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void auth_storesStreamKey() {
    when(redis.opsForValue()).thenReturn(valueOps);
    Map<String, Object> out = service.auth("openai", "gpt", List.of(), "zh-CN", "s1", null);
    assertTrue(out.containsKey("streamKey"));
    verify(valueOps).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  void models_filtersAiBotSetting() {
    when(settings.findSettingJson("aiBotSetting"))
        .thenReturn(Optional.of("{\"openaiModels\":[\"a\"],\"secret\":\"x\"}"));
    Map<String, Object> out = service.models();
    assertTrue(out.containsKey("openaiModels"));
    assertFalse(out.containsKey("secret"));
  }

  @Test
  void matchElements_ranksByName() {
    Map<String, Object> out =
        service.matchElements(
            "create task",
            List.of(
                Map.of("ref", "1", "name", "Create Task"),
                Map.of("ref", "2", "name", "Delete Project")),
            10);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> matches = (List<Map<String, Object>>) out.get("matches");
    assertFalse(matches.isEmpty());
    assertEquals("1", ((Map<?, ?>) matches.get(0).get("element")).get("ref"));
    assertEquals("lexical", out.get("strategy"));
  }

  @Test
  void matchElements_usesEmbeddingWhenAvailable() {
    com.bluedock.system.ai.AiBotChatService chat = org.mockito.Mockito.mock(com.bluedock.system.ai.AiBotChatService.class);
    when(aiChat.getIfAvailable()).thenReturn(chat);
    when(chat.available()).thenReturn(true);
    when(chat.embed(any()))
        .thenReturn(
            List.of(
                new float[] {1f, 0f},
                new float[] {0.9f, 0.1f},
                new float[] {0f, 1f}));
    Map<String, Object> out =
        service.matchElements(
            "q",
            List.of(Map.of("ref", "a", "name", "A"), Map.of("ref", "b", "name", "B")),
            10);
    assertEquals("embedding", out.get("strategy"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> matches = (List<Map<String, Object>>) out.get("matches");
    assertEquals("a", ((Map<?, ?>) matches.get(0).get("element")).get("ref"));
  }

  @Test
  void sessionSave_requiresSessionId() {
    assertThrows(
        BusinessException.class,
        () -> service.sessionSave("default", "", "", "t", List.of(), null));
  }

  @Test
  void sessionSave_upserts() {
    when(sessions.find(7L, "default", "sid-1")).thenReturn(Optional.empty());
    Map<String, Object> out = service.sessionSave("default", "sid-1", "chat", "Hello", List.of(), null);
    assertTrue(out.containsKey("imageUrls"));
    ArgumentCaptor<AssistantSession> cap = ArgumentCaptor.forClass(AssistantSession.class);
    verify(sessions).upsert(cap.capture());
    assertEquals("sid-1", cap.getValue().getSessionId());
    assertEquals("Hello", cap.getValue().getTitle());
  }

  @Test
  @SuppressWarnings("unchecked")
  void sessionSave_newImages_uploadsAndMerges() throws Exception {
    when(sessions.find(7L, "default", "sid-1")).thenReturn(Optional.empty());
    when(storage.put(anyString(), any(), anyLong(), anyString()))
        .thenReturn("http://cdn/media/assistant/a.png");
    String b64 = java.util.Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
    Map<String, Object> out =
        service.sessionSave(
            "default",
            "sid-1",
            "chat",
            "Hello",
            List.of(),
            Map.of("pic1", "data:image/png;base64," + b64));
    Map<String, String> urls = (Map<String, String>) out.get("imageUrls");
    assertEquals("http://cdn/media/assistant/a.png", urls.get("pic1"));
    ArgumentCaptor<AssistantSession> cap = ArgumentCaptor.forClass(AssistantSession.class);
    verify(sessions).upsert(cap.capture());
    assertTrue(cap.getValue().getImagesJson().contains("pic1"));
  }

  @Test
  void sessionSave_newImages_keepsUrl() {
    when(sessions.find(7L, "default", "sid-1")).thenReturn(Optional.empty());
    Map<String, Object> out =
        service.sessionSave(
            "default",
            "sid-1",
            "chat",
            "Hello",
            List.of(),
            Map.of("a", "https://cdn.example/x.png"));
    @SuppressWarnings("unchecked")
    Map<String, String> urls = (Map<String, String>) out.get("imageUrls");
    assertEquals("https://cdn.example/x.png", urls.get("a"));
    verify(storage, org.mockito.Mockito.never()).put(anyString(), any(), anyLong(), anyString());
  }

  @Test
  void operationDispatch_publishesFanout() {
    when(redis.opsForSet()).thenReturn(setOps);
    when(setOps.members(RedisKeys.wsUser(7L))).thenReturn(Set.of("sess-a"));
    Map<String, Object> out = service.operationDispatch("", "get_page_context", Map.of());
    assertTrue(out.get("requestId").toString().length() > 0);
    ArgumentCaptor<RealtimeFanoutEvent> cap = ArgumentCaptor.forClass(RealtimeFanoutEvent.class);
    verify(fanout).publish(cap.capture());
    assertEquals(RealtimeEventTypes.OPERATION, cap.getValue().type());
    assertEquals(List.of(7L), cap.getValue().userIds());
  }

  @Test
  void operationResult_pendingWhenMissing() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(eq(RedisKeys.assistantOp("reportId")))).thenReturn(null);
    assertEquals("pending", service.operationResult("reportId").get("status"));
  }

  @Test
  void feedbackSave_like() {
    Map<String, Object> out =
        service.feedbackSave("default", "s1", 3L, "like", "q", "a", List.of(), "m");
    assertEquals("like", out.get("feedback"));
    verify(feedbacks)
        .upsert(eq(7L), eq("default"), eq("s1"), eq(3L), eq("like"), any(), any(), any(), any(), eq("m"));
  }
}
