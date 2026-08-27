package com.bluedock.worker.notify.consumer;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.worker.notify.handler.UserBotWebhookHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class UserBotWebhookConsumer {
  private static final Logger log = LoggerFactory.getLogger(UserBotWebhookConsumer.class);

  private final ObjectMapper objectMapper;
  private final UserBotWebhookHandler handler;

  public UserBotWebhookConsumer(ObjectMapper objectMapper, UserBotWebhookHandler handler) {
    this.objectMapper = objectMapper;
    this.handler = handler;
  }

  @KafkaListener(topics = KafkaTopics.USER_BOT_WEBHOOK, groupId = ConsumerGroups.NOTIFY)
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      UserBotWebhookEvent event = objectMapper.readValue(payload, UserBotWebhookEvent.class);
      handler.handle(event);
    } catch (Exception e) {
      log.warn("bot webhook consume failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
