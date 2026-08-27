package com.bluedock.task.export;

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.export.ExportRunEvent;
import com.bluedock.common.export.ExportRunPublisher;
import com.bluedock.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaExportRunPublisher implements ExportRunPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaExportRunPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;

  public KafkaExportRunPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(ExportRunEvent event) {
    if (event == null) {
      return;
    }
    try {
      kafka.send(
          KafkaTopics.EXPORT_RUN, event.eventId(), objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException e) {
      log.warn("export serialize failed: {}", e.toString());
    }
  }
}
