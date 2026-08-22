package com.bluedock.boot.outbox;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Outbox 批量子投递（独立 Bean，保证 {@code @Transactional} 生效）。 */
@Service
@EnableConfigurationProperties(OutboxProperties.class)
@ConditionalOnProperty(
    prefix = "bluedock.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxDispatchService {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);

  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final OutboxProperties props;

  public OutboxDispatchService(
      JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, OutboxProperties props) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.props = props;
  }

  @Transactional
  public int pollOnce() {
    int limit = Math.max(1, props.getBatchSize());
    List<OutboxRow> rows =
        jdbc.query(
            """
            SELECT id, topic, message_key, payload
            FROM bluedock_outbox
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
            (rs, i) ->
                new OutboxRow(
                    rs.getLong("id"),
                    rs.getString("topic"),
                    rs.getString("message_key"),
                    rs.getString("payload")),
            limit);
    if (rows.isEmpty()) {
      return 0;
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    int ok = 0;
    for (OutboxRow row : rows) {
      try {
        if (row.messageKey() == null || row.messageKey().isBlank()) {
          kafka.send(row.topic(), row.payload()).get(10, TimeUnit.SECONDS);
        } else {
          kafka.send(row.topic(), row.messageKey(), row.payload()).get(10, TimeUnit.SECONDS);
        }
        jdbc.update("UPDATE bluedock_outbox SET published_at = ? WHERE id = ?", now, row.id());
        ok++;
      } catch (Exception e) {
        log.warn("outbox publish failed id={} topic={}: {}", row.id(), row.topic(), e.toString());
      }
    }
    return ok;
  }

  private record OutboxRow(long id, String topic, String messageKey, String payload) {}
}
