package com.bluedock.messenger.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.export.ExportNotifyBridge;
import com.bluedock.common.export.ExportNotifyEvent;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
class ExportNotifyConsumerTest {
  @Mock ExportNotifyBridge bridge;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> valueOps;
  @Mock Acknowledgment ack;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void compose_prefersBodyWhenPrefixedByTitle() {
    assertEquals(
        "导出已完成，点击下载：/x",
        ExportNotifyConsumer.compose("导出已完成", "导出已完成，点击下载：/x"));
    assertEquals("仅标题", ExportNotifyConsumer.compose("仅标题", ""));
    assertEquals("仅正文", ExportNotifyConsumer.compose("", "仅正文"));
    assertEquals("标题\n正文", ExportNotifyConsumer.compose("标题", "正文"));
  }

  @Test
  void onMessage_sendsDm() throws Exception {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(
            eq(RedisKeys.exportNotifyIdempotency("e1")), eq("1"), org.mockito.ArgumentMatchers.any(Duration.class)))
        .thenReturn(true);
    when(bridge.sendDm(9L, "导出任务统计已完成，点击下载：/api/project/task/download?key=k"))
        .thenReturn(42L);

    ExportNotifyConsumer consumer = new ExportNotifyConsumer(objectMapper, bridge, redis);
    String payload =
        objectMapper.writeValueAsString(
            new ExportNotifyEvent(
                "e1",
                9L,
                "导出任务统计已完成",
                "导出任务统计已完成，点击下载：/api/project/task/download?key=k"));
    consumer.onMessage(payload, ack);

    verify(bridge)
        .sendDm(9L, "导出任务统计已完成，点击下载：/api/project/task/download?key=k");
    verify(ack).acknowledge();
  }

  @Test
  void onMessage_idempotentSkip() throws Exception {
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.setIfAbsent(
            eq(RedisKeys.exportNotifyIdempotency("e1")), eq("1"), org.mockito.ArgumentMatchers.any(Duration.class)))
        .thenReturn(false);

    ExportNotifyConsumer consumer = new ExportNotifyConsumer(objectMapper, bridge, redis);
    String payload =
        objectMapper.writeValueAsString(new ExportNotifyEvent("e1", 9L, "t", "b"));
    consumer.onMessage(payload, ack);

    verify(bridge, never()).sendDm(org.mockito.ArgumentMatchers.anyLong(), anyString());
    verify(ack).acknowledge();
  }
}
