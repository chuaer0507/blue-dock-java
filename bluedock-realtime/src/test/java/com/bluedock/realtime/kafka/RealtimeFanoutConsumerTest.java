package com.bluedock.realtime.kafka;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.realtime.ws.WsSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class RealtimeFanoutConsumerTest {
  @Test
  void onMessage_pushes() {
    WsSessionRegistry sessions = mock(WsSessionRegistry.class);
    RealtimeFanoutConsumer consumer = new RealtimeFanoutConsumer(new ObjectMapper(), sessions);
    Acknowledgment ack = mock(Acknowledgment.class);

    String payload =
        """
        {"eventId":"e1","type":"dialog.message","userIds":[1,2],"data":{"dialogId":9}}
        """;
    consumer.onMessage(payload, ack);

    verify(sessions).pushToUser(eq(1L), org.mockito.ArgumentMatchers.contains("dialog.message"));
    verify(sessions).pushToUser(eq(2L), org.mockito.ArgumentMatchers.contains("dialog.message"));
    verify(ack).acknowledge();
  }
}
