package com.bluedock.worker.notify.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ExportRunConsumer {
  private static final Logger log = LoggerFactory.getLogger(ExportRunConsumer.class);

  private final ObjectMapper objectMapper;
  private final ExportRunHandler handler;

  public ExportRunConsumer(ObjectMapper objectMapper, ExportRunHandler handler) {
    this.objectMapper = objectMapper;
    this.handler = handler;
  }

  @KafkaListener(topics = KafkaTopics.EXPORT_RUN, groupId = ConsumerGroups.EXPORT)
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      ExportRunEvent event = objectMapper.readValue(payload, ExportRunEvent.class);
      handler.handle(event);
    } catch (Exception e) {
      log.warn("export consume failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
