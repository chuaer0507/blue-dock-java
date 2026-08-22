package com.bluedock.worker.notify.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.bot.UserBotWebhookReplyEvent;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.common.redis.RedisKeys;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 机器人 Webhook HTTP 投递：form-urlencoded POST，超时 30s，失败不重试。
 * 响应 {@code {"code":200,"message":"..."}} 时发回复事件。
 */
@Component
public class UserBotWebhookHandler {
  private static final Logger log = LoggerFactory.getLogger(UserBotWebhookHandler.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final StringRedisTemplate redis;
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

  public UserBotWebhookHandler(
      StringRedisTemplate redis,
      JdbcTemplate jdbc,
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper) {
    this.redis = redis;
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.objectMapper = objectMapper;
  }

  public void handle(UserBotWebhookEvent event) {
    if (event == null || event.webhookUrl() == null || event.webhookUrl().isBlank()) {
      return;
    }
    if (event.eventId() != null && !event.eventId().isBlank()) {
      Boolean first =
          redis
              .opsForValue()
              .setIfAbsent(RedisKeys.userBotWebhookIdempotency(event.eventId()), "1", Duration.ofDays(2));
      if (Boolean.FALSE.equals(first)) {
        log.debug("bot webhook idempotency skip eventId={}", event.eventId());
        return;
      }
    }

    try {
      String body = toForm(event);
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(event.webhookUrl()))
              .timeout(TIMEOUT)
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      jdbc.update(
          "UPDATE bluedock_user_bots SET webhook_count = webhook_count + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE bot_id = ?",
          event.botUserId());
      maybeReply(event, resp.body());
    } catch (Exception e) {
      log.info(
          "bot webhook failed url={} botUserId={} err={}",
          event.webhookUrl(),
          event.botUserId(),
          e.toString());
    }
  }

  private void maybeReply(UserBotWebhookEvent event, String responseBody) {
    if (responseBody == null || responseBody.isBlank()) {
      return;
    }
    if (!UserBotWebhookEvent.EVENT_MESSAGE.equals(event.event())) {
      return;
    }
    try {
      JsonNode node = objectMapper.readTree(responseBody);
      if (node.path("code").asInt(0) != 200) {
        return;
      }
      String message = node.path("message").asText("");
      if (message.isBlank()) {
        return;
      }
      String replyId = event.eventId() + ":reply";
      UserBotWebhookReplyEvent reply =
          new UserBotWebhookReplyEvent(replyId, event.botUserId(), event.dialogId(), event.messageId(), message);
      kafka.send(
          KafkaTopics.USER_BOT_WEBHOOK_REPLY,
          replyId,
          objectMapper.writeValueAsString(reply));
    } catch (Exception e) {
      log.debug("bot webhook response parse skip: {}", e.toString());
    }
  }

  private String toForm(UserBotWebhookEvent e) throws Exception {
    StringJoiner joiner = new StringJoiner("&");
    put(joiner, "event", e.event());
    put(joiner, "text", e.text());
    put(joiner, "replyText", e.replyText());
    put(joiner, "token", e.token());
    put(joiner, "sessionId", "");
    put(joiner, "dialogId", Long.toString(e.dialogId()));
    put(joiner, "dialogType", e.dialogType());
    put(joiner, "groupType", e.groupType());
    put(joiner, "dialogName", e.dialogName());
    put(joiner, "messageId", Long.toString(e.messageId()));
    put(joiner, "messageUserId", Long.toString(e.messageUserId()));
    put(joiner, "mention", Integer.toString(e.mention()));
    put(joiner, "botUserId", Long.toString(e.botUserId()));
    put(joiner, "extras", e.extras());
    put(joiner, "version", e.version());
    put(joiner, "timestamp", Long.toString(e.timestamp()));
    if (e.messageUser() != null && !e.messageUser().isEmpty()) {
      for (Map.Entry<String, Object> entry : e.messageUser().entrySet()) {
        put(
            joiner,
            "messageUser[" + entry.getKey() + "]",
            entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
      }
    }
    putNested(joiner, "member", e.member());
    putNested(joiner, "operator", e.operator());
    return joiner.toString();
  }

  private static void putNested(StringJoiner joiner, String prefix, Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return;
    }
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      put(
          joiner,
          prefix + "[" + entry.getKey() + "]",
          entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
    }
  }

  private static void put(StringJoiner joiner, String key, String value) {
    joiner.add(
        URLEncoder.encode(key, StandardCharsets.UTF_8)
            + "="
            + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
  }
}
