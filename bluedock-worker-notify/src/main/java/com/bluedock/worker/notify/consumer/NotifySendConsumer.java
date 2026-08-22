package com.bluedock.worker.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.worker.notify.handler.NotifySendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class NotifySendConsumer {
  private static final Logger log = LoggerFactory.getLogger(NotifySendConsumer.class);

  private final ObjectMapper objectMapper;
  private final NotifySendHandler handler;

  public NotifySendConsumer(ObjectMapper objectMapper, NotifySendHandler handler) {
    this.objectMapper = objectMapper;
    this.handler = handler;
  }

  @KafkaListener(topics = KafkaTopics.NOTIFY_SEND, groupId = ConsumerGroups.NOTIFY)
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      NotifySendEvent event = objectMapper.readValue(payload, NotifySendEvent.class);
      handler.handle(event);
    } catch (Exception e) {
      log.warn("notify consume failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
