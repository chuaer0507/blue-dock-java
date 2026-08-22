package com.bluedock.messenger.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.bot.UserBotWebhookReplyEvent;
import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.messenger.service.DialogService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class UserBotWebhookReplyConsumer {
  private static final Logger log = LoggerFactory.getLogger(UserBotWebhookReplyConsumer.class);

  private final ObjectMapper objectMapper;
  private final DialogService dialogs;
  private final StringRedisTemplate redis;

  public UserBotWebhookReplyConsumer(
      ObjectMapper objectMapper, DialogService dialogs, StringRedisTemplate redis) {
    this.objectMapper = objectMapper;
    this.dialogs = dialogs;
    this.redis = redis;
  }

  @KafkaListener(topics = KafkaTopics.USER_BOT_WEBHOOK_REPLY, groupId = ConsumerGroups.USER_BOT_WEBHOOK_REPLY)
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      UserBotWebhookReplyEvent event = objectMapper.readValue(payload, UserBotWebhookReplyEvent.class);
      if (event == null || event.text() == null || event.text().isBlank()) {
        return;
      }
      if (event.eventId() != null && !event.eventId().isBlank()) {
        Boolean first =
            redis
                .opsForValue()
                .setIfAbsent(
                    RedisKeys.userBotWebhookReplyIdempotency(event.eventId()), "1", Duration.ofDays(2));
        if (Boolean.FALSE.equals(first)) {
          return;
        }
      }
      dialogs.sendTextAsBot(event.botUserId(), event.dialogId(), event.text());
    } catch (Exception e) {
      log.warn("bot webhook reply failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
