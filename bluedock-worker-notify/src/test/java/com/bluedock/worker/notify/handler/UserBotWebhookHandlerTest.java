package com.bluedock.worker.notify.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class UserBotWebhookHandlerTest {
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @Mock JdbcTemplate jdbc;
  @Mock KafkaTemplate<String, String> kafka;

  @Test
  void idempotency_skips() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.setIfAbsent(eq(RedisKeys.userBotWebhookIdempotency("e1")), eq("1"), any(Duration.class)))
        .thenReturn(false);
    UserBotWebhookHandler handler = new UserBotWebhookHandler(redis, jdbc, kafka, new ObjectMapper());
    handler.handle(
        new UserBotWebhookEvent(
            "e1",
            "message",
            "http://example.com/hook",
            2L,
            3L,
            "user",
            4L,
            1L,
            0,
            "hi",
            "",
            "tok",
            Map.of(),
            "{}",
            "1.0.0",
            1L,
            null,
            null,
            "",
            ""));
    verify(jdbc, never()).update(any(String.class), any(Object.class));
  }
}
