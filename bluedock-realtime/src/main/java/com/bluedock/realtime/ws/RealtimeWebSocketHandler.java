package com.bluedock.realtime.ws;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.realtime.RealtimeEventTypes;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

  private final WsSessionRegistry sessions;
  private final ObjectMapper objectMapper;
  private final OperationResultStore operationResults;

  public RealtimeWebSocketHandler(
      WsSessionRegistry sessions, ObjectMapper objectMapper, OperationResultStore operationResults) {
    this.sessions = sessions;
    this.objectMapper = objectMapper;
    this.operationResults = operationResults;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    Object uid = session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
    if (!(uid instanceof Long userId)) {
      try {
        session.close(CloseStatus.NOT_ACCEPTABLE);
      } catch (Exception ignored) {
        // ignore
      }
      return;
    }
    sessions.register(userId, session, clientOf(session));
    log.debug("ws connected userId={} session={}", userId, session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String payload = message.getPayload();
    if (payload == null || payload.isBlank()) {
      return;
    }
    if ("ping".equalsIgnoreCase(payload.trim())) {
      sessions.touchPresence(session);
      session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type", RealtimeEventTypes.PONG))));
      return;
    }
    JsonNode node = objectMapper.readTree(payload);
    String type = node.path("type").asString("");
    if (RealtimeEventTypes.PING.equals(type)) {
      sessions.touchPresence(session);
      session.sendMessage(
          new TextMessage(
              objectMapper.writeValueAsString(Map.of("type", RealtimeEventTypes.PONG))));
      return;
    }
    if (RealtimeEventTypes.OPERATION_RESULT.equals(type)) {
      Long userId = sessions.userIdOf(session);
      if (userId == null) {
        return;
      }
      JsonNode data = node.path("data");
      String requestId = data.path("requestId").asString("");
      boolean success = data.path("success").asBoolean(false);
      Object result =
          data.has("result") ? objectMapper.convertValue(data.get("result"), Object.class) : null;
      String error = data.has("error") && !data.get("error").isNull() ? data.get("error").asString() : null;
      operationResults.save(userId, requestId, success, result, error);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.unregister(session);
  }

  private static String clientOf(WebSocketSession session) {
    Object client = session.getAttributes().get(AuthHandshakeInterceptor.ATTR_CLIENT);
    return client instanceof String s ? s : null;
  }
}
