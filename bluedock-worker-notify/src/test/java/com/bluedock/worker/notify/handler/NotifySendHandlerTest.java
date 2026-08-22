package com.bluedock.worker.notify.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.worker.notify.channel.EmailNotifyChannel;
import com.bluedock.worker.notify.channel.AppPushChannel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class NotifySendHandlerTest {
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> valueOps;
  @Mock EmailNotifyChannel email;
  @Mock AppPushChannel push;

  @Test
  void handle_idempotency_and_route_push() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    NotifySendHandler handler = new NotifySendHandler(redis, email, push);
    NotifySendEvent event =
        new NotifySendEvent("e1", NotifySendEvent.CHANNEL_PUSH, List.of(1L), "Hi", "body", Map.of());
    handler.handle(event);
    verify(valueOps).setIfAbsent(eq(RedisKeys.notifyIdempotency("e1")), eq("1"), any(Duration.class));
    verify(push).deliver(event);
    verify(email, never()).deliver(any());
  }

  @Test
  void handle_route_email() {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    NotifySendHandler handler = new NotifySendHandler(redis, email, push);
    NotifySendEvent event =
        new NotifySendEvent(
            "e2", NotifySendEvent.CHANNEL_EMAIL, List.of(1L), "Hi", "body", Map.of());
    handler.handle(event);
    verify(email).deliver(event);
    verify(push, never()).deliver(any());
  }
}
