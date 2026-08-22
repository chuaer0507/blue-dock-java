package com.bluedock.worker.index.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.worker.index.handler.SearchIndexHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexConsumer {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexConsumer.class);

  private final ObjectMapper objectMapper;
  private final SearchIndexHandler handler;

  public SearchIndexConsumer(ObjectMapper objectMapper, SearchIndexHandler handler) {
    this.objectMapper = objectMapper;
    this.handler = handler;
  }

  @KafkaListener(topics = KafkaTopics.SEARCH_INDEX, groupId = ConsumerGroups.INDEX)
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      SearchIndexEvent event = objectMapper.readValue(payload, SearchIndexEvent.class);
      handler.handle(event);
    } catch (Exception e) {
      log.warn("search index consume failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
