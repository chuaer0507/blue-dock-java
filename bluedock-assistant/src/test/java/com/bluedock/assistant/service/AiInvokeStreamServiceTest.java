package com.bluedock.assistant.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.system.ai.AiBotChatService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class AiInvokeStreamServiceTest {
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock AiBotChatService chat;

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void stream_expiredKey() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.getAndDelete(RedisKeys.assistantStream("abc"))).thenReturn(null);
    AiInvokeStreamService service = new AiInvokeStreamService(redis, json, chat);
    assertThrows(BusinessException.class, () -> service.stream("abc"));
  }

  @Test
  void stream_emitsViaChatStream() throws Exception {
    when(redis.opsForValue()).thenReturn(values);
    String payload =
        json.writeValueAsString(
            Map.of(
                "userId",
                1L,
                "modelName",
                "gpt-4o-mini",
                "context",
                List.of(List.of("user", "hi"))));
    when(values.getAndDelete(RedisKeys.assistantStream("sk1"))).thenReturn(payload);
    when(chat.available()).thenReturn(true);
    when(chat.systemPrompt()).thenReturn("");
    CountDownLatch latch = new CountDownLatch(1);
    doAnswer(
            inv -> {
              Consumer<String> onDelta = inv.getArgument(2);
              onDelta.accept("hello");
              latch.countDown();
              return null;
            })
        .when(chat)
        .chatStream(eq("gpt-4o-mini"), any(), any());

    AiInvokeStreamService service = new AiInvokeStreamService(redis, json, chat);
    SseEmitter emitter = service.stream("sk1");
    assertNotNull(emitter);
    latch.await(3, TimeUnit.SECONDS);
    verify(chat).chatStream(eq("gpt-4o-mini"), any(), any());
  }

  @Test
  void stream_aiUnavailable() throws Exception {
    when(redis.opsForValue()).thenReturn(values);
    when(values.getAndDelete(anyString()))
        .thenReturn(json.writeValueAsString(Map.of("context", List.of(List.of("user", "a")))));
    when(chat.available()).thenReturn(false);
    AiInvokeStreamService service = new AiInvokeStreamService(redis, json, chat);
    assertThrows(BusinessException.class, () -> service.stream("x"));
  }
}
