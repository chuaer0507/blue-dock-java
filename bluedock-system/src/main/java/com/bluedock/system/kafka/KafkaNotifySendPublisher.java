package com.bluedock.system.kafka;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KafkaNotifySendPublisher implements NotifySendPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaNotifySendPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<OutboxWriter> outboxWriter;

  public KafkaNotifySendPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper,
      ObjectProvider<OutboxWriter> outboxWriter) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.outboxWriter = outboxWriter;
  }

  @Override
  public void publish(NotifySendEvent event) {
    if (event == null) {
      return;
    }
    try {
      String key = event.eventId();
      String json = objectMapper.writeValueAsString(event);
      OutboxWriter outbox = outboxWriter.getIfAvailable();
      if (outbox != null && TransactionSynchronizationManager.isActualTransactionActive()) {
        outbox.enqueue(KafkaTopics.NOTIFY_SEND, key, json);
        return;
      }
      kafka.send(KafkaTopics.NOTIFY_SEND, key, json);
    } catch (JacksonException e) {
      log.warn("notify serialize failed: {}", e.toString());
    }
  }
}
