package com.bluedock.messenger.export;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.export.ExportNotifyBridge;
import com.bluedock.common.export.ExportNotifyEvent;
import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ExportNotifyConsumer {
  private static final Logger log = LoggerFactory.getLogger(ExportNotifyConsumer.class);

  private final ObjectMapper objectMapper;
  private final ExportNotifyBridge bridge;
  private final StringRedisTemplate redis;

  public ExportNotifyConsumer(
      ObjectMapper objectMapper, ExportNotifyBridge bridge, StringRedisTemplate redis) {
    this.objectMapper = objectMapper;
    this.bridge = bridge;
    this.redis = redis;
  }

  @KafkaListener(topics = KafkaTopics.EXPORT_NOTIFY, groupId = ConsumerGroups.EXPORT_NOTIFY)
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      ExportNotifyEvent event = objectMapper.readValue(payload, ExportNotifyEvent.class);
      if (event == null || event.userId() <= 0) {
        return;
      }
      String text = compose(event.title(), event.body());
      if (text.isBlank()) {
        return;
      }
      if (event.eventId() != null && !event.eventId().isBlank()) {
        Boolean first =
            redis
                .opsForValue()
                .setIfAbsent(
                    RedisKeys.exportNotifyIdempotency(event.eventId()), "1", Duration.ofDays(2));
        if (Boolean.FALSE.equals(first)) {
          return;
        }
      }
      long messageId = bridge.sendDm(event.userId(), text);
      if (messageId <= 0) {
        log.debug("export notify dm skipped userId={}", event.userId());
      }
    } catch (Exception e) {
      log.warn("export notify failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }

  static String compose(String title, String body) {
    String t = title == null ? "" : title.trim();
    String b = body == null ? "" : body.trim();
    if (t.isEmpty()) {
      return b;
    }
    if (b.isEmpty()) {
      return t;
    }
    if (b.startsWith(t)) {
      return b;
    }
    return t + "\n" + b;
  }
}
