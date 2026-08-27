package com.bluedock.realtime.kafka;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.realtime.ws.WsSessionRegistry;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class RealtimeFanoutConsumer {
  private static final Logger log = LoggerFactory.getLogger(RealtimeFanoutConsumer.class);

  private final ObjectMapper objectMapper;
  private final WsSessionRegistry sessions;

  public RealtimeFanoutConsumer(ObjectMapper objectMapper, WsSessionRegistry sessions) {
    this.objectMapper = objectMapper;
    this.sessions = sessions;
  }

  @KafkaListener(
      topics = KafkaTopics.REALTIME_FANOUT,
      groupId = "#{@realtimeConsumerGroupId}")
  public void onMessage(String payload, Acknowledgment ack) {
    try {
      RealtimeFanoutEvent event = objectMapper.readValue(payload, RealtimeFanoutEvent.class);
      if (event.userIds() == null || event.userIds().isEmpty()) {
        return;
      }
      Map<String, Object> frame = new HashMap<>();
      frame.put("type", event.type());
      frame.put("eventId", event.eventId());
      frame.put("data", event.data() == null ? Map.of() : event.data());
      String json = objectMapper.writeValueAsString(frame);
      for (Long userId : event.userIds()) {
        if (userId != null) {
          sessions.pushToUser(userId, json);
        }
      }
    } catch (Exception e) {
      log.warn("fanout consume failed: {}", e.toString());
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
