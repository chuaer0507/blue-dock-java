package com.bluedock.user.bot.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserBotRepository {
  private final JdbcTemplate jdbc;

  public UserBotRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public int countOwned(long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_bots WHERE user_id = ?", Integer.class, userId);
    return n == null ? 0 : n;
  }

  public List<Map<String, Object>> listOwned(long userId) {
    return jdbc.query(
        """
        SELECT u.id AS id, u.nickname AS name, u.user_img AS avatar, u.email AS email,
               b.clear_day AS clearDay, b.webhook_url AS webhookUrl, b.webhook_events AS webhookEvents
        FROM bluedock_user_bots b
        JOIN bluedock_users u ON u.id = b.bot_id
        WHERE b.user_id = ? AND u.is_bot = 1
        ORDER BY b.id DESC
        """,
        (rs, i) -> {
          Map<String, Object> m = new java.util.LinkedHashMap<>();
          m.put("id", rs.getLong("id"));
          m.put("name", rs.getString("name"));
          m.put("avatar", rs.getString("avatar") == null ? "" : rs.getString("avatar"));
          m.put("email", rs.getString("email"));
          m.put("clearDay", rs.getInt("clearDay"));
          m.put("webhookUrl", rs.getString("webhookUrl") == null ? "" : rs.getString("webhookUrl"));
          m.put("webhookEvents", rs.getString("webhookEvents"));
          return m;
        },
        userId);
  }

  public Optional<Map<String, Object>> findOwned(long ownerId, long botId) {
    var list =
        jdbc.query(
            """
            SELECT id, user_id, bot_id, clear_day, webhook_url, webhook_events
            FROM bluedock_user_bots WHERE user_id = ? AND bot_id = ?
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> m = new java.util.LinkedHashMap<>();
              m.put("id", rs.getLong("id"));
              m.put("userId", rs.getLong("user_id"));
              m.put("botId", rs.getLong("bot_id"));
              m.put("clearDay", rs.getInt("clear_day"));
              m.put("webhookUrl", rs.getString("webhook_url") == null ? "" : rs.getString("webhook_url"));
              m.put("webhookEvents", rs.getString("webhook_events"));
              return m;
            },
            ownerId,
            botId);
    return list.stream().findFirst();
  }

  public long insert(long ownerId, long botId, int clearDay, String webhookUrl, String eventsJson) {
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_bots
          (id, user_id, bot_id, clear_day, webhook_url, webhook_events, webhook_count, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
        """,
        id,
        ownerId,
        botId,
        clearDay,
        webhookUrl,
        eventsJson,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return id;
  }

  public void update(long ownerId, long botId, Integer clearDay, String webhookUrl, String eventsJson) {
    if (clearDay != null) {
      jdbc.update(
          """
          UPDATE bluedock_user_bots SET clear_day = ?, updated_at = ?
          WHERE user_id = ? AND bot_id = ?
          """,
          clearDay,
          Timestamp.valueOf(LocalDateTime.now()),
          ownerId,
          botId);
    }
    if (webhookUrl != null) {
      jdbc.update(
          """
          UPDATE bluedock_user_bots SET webhook_url = ?, updated_at = ?
          WHERE user_id = ? AND bot_id = ?
          """,
          webhookUrl,
          Timestamp.valueOf(LocalDateTime.now()),
          ownerId,
          botId);
    }
    if (eventsJson != null) {
      jdbc.update(
          """
          UPDATE bluedock_user_bots SET webhook_events = ?, updated_at = ?
          WHERE user_id = ? AND bot_id = ?
          """,
          eventsJson,
          Timestamp.valueOf(LocalDateTime.now()),
          ownerId,
          botId);
    }
  }

  public void delete(long ownerId, long botId) {
    jdbc.update("DELETE FROM bluedock_user_bots WHERE user_id = ? AND bot_id = ?", ownerId, botId);
  }
}
