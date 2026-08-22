package com.bluedock.realtime.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.outbox.OutboxWriter;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KafkaRealtimeFanoutPublisher implements RealtimeFanoutPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaRealtimeFanoutPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<OutboxWriter> outboxWriter;

  public KafkaRealtimeFanoutPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper,
      ObjectProvider<OutboxWriter> outboxWriter) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.outboxWriter = outboxWriter;
  }

  @Override
  public void publish(RealtimeFanoutEvent event) {
    if (event == null || event.userIds() == null || event.userIds().isEmpty()) {
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(event);
      String key = event.eventId();
      OutboxWriter outbox = outboxWriter.getIfAvailable();
      if (outbox != null && TransactionSynchronizationManager.isActualTransactionActive()) {
        outbox.enqueue(KafkaTopics.REALTIME_FANOUT, key, json);
        return;
      }
      kafka.send(KafkaTopics.REALTIME_FANOUT, key, json);
    } catch (JsonProcessingException e) {
      log.warn("fanout serialize failed type={}", event.type(), e);
    }
  }
}
