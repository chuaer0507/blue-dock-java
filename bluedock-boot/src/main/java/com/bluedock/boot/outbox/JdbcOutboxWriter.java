package com.bluedock.boot.outbox;

import com.bluedock.common.outbox.OutboxWriter;
import com.bluedock.common.util.IdGenerator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/** 与业务同事务写入 {@code bluedock_outbox}。 */
@Component
@ConditionalOnProperty(
    prefix = "bluedock.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JdbcOutboxWriter implements OutboxWriter {

  private final JdbcTemplate jdbc;

  public JdbcOutboxWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void enqueue(String topic, String messageKey, String payload) {
    Assert.hasText(topic, "topic required");
    Assert.hasText(payload, "payload required");
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    jdbc.update(
        """
        INSERT INTO bluedock_outbox (id, topic, message_key, payload, created_at, published_at)
        VALUES (?, ?, ?, ?, ?, NULL)
        """,
        id,
        topic,
        blankToNull(messageKey),
        payload,
        now);
  }

  private static String blankToNull(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    return s;
  }
}
