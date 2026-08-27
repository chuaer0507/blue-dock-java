package com.bluedock.search.kafka;

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.outbox.OutboxWriter;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class KafkaSearchIndexPublisher implements SearchIndexPublisher {
  private static final Logger log = LoggerFactory.getLogger(KafkaSearchIndexPublisher.class);

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final ObjectProvider<OutboxWriter> outboxWriter;

  public KafkaSearchIndexPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper,
      ObjectProvider<OutboxWriter> outboxWriter) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.outboxWriter = outboxWriter;
  }

  @Override
  public void publish(SearchIndexEvent event) {
    if (event == null) {
      return;
    }
    try {
      String key = event.docType() + ":" + event.refId();
      String json = objectMapper.writeValueAsString(event);
      OutboxWriter outbox = outboxWriter.getIfAvailable();
      if (outbox != null && TransactionSynchronizationManager.isActualTransactionActive()) {
        outbox.enqueue(KafkaTopics.SEARCH_INDEX, key, json);
        return;
      }
      kafka.send(KafkaTopics.SEARCH_INDEX, key, json);
    } catch (JsonProcessingException e) {
      log.warn("search index serialize failed: {}", e.toString());
    }
  }
}
