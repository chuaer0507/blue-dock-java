package com.bluedock.messenger.bot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.bot.UserBotWebhookPublisher;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KafkaUserBotWebhookPublisher implements UserBotWebhookPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaUserBotWebhookPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<OutboxWriter> outboxWriter;

  public KafkaUserBotWebhookPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper,
      ObjectProvider<OutboxWriter> outboxWriter) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.outboxWriter = outboxWriter;
  }

  @Override
  public void publish(UserBotWebhookEvent event) {
    if (event == null) {
      return;
    }
    try {
      String key = event.eventId();
      String json = objectMapper.writeValueAsString(event);
      OutboxWriter outbox = outboxWriter.getIfAvailable();
      if (outbox != null && TransactionSynchronizationManager.isActualTransactionActive()) {
        outbox.enqueue(KafkaTopics.USER_BOT_WEBHOOK, key, json);
        return;
      }
      kafka.send(KafkaTopics.USER_BOT_WEBHOOK, key, json);
    } catch (JacksonException e) {
      log.warn("bot webhook serialize failed: {}", e.toString());
    }
  }
}
