package com.bluedock.user.browse.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecentItemRepository {
  private final JdbcTemplate jdbc;

  public RecentItemRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void upsert(
      long userId, String targetType, long targetId, String sourceType, long sourceId) {
    LocalDateTime now = LocalDateTime.now();
    int n =
        jdbc.update(
            """
            UPDATE bluedock_user_recent_items
            SET browsed_at = ?, updated_at = ?
            WHERE user_id = ? AND target_type = ? AND target_id = ?
              AND source_type = ? AND source_id = ?
            """,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            userId,
            targetType,
            targetId,
            sourceType,
            sourceId);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_user_recent_items
            (id, user_id, target_type, target_id, source_type, source_id, browsed_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          userId,
          targetType,
          targetId,
          sourceType,
          sourceId,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public int count(long userId, String typeOrNull) {
    Integer n;
    if (typeOrNull == null || typeOrNull.isBlank()) {
      n =
          jdbc.queryForObject(
              "SELECT COUNT(1) FROM bluedock_user_recent_items WHERE user_id = ?",
              Integer.class,
              userId);
    } else {
      n =
          jdbc.queryForObject(
              "SELECT COUNT(1) FROM bluedock_user_recent_items WHERE user_id = ? AND target_type = ?",
              Integer.class,
              userId,
              typeOrNull);
    }
    return n == null ? 0 : n;
  }

  public List<Map<String, Object>> page(long userId, String typeOrNull, int offset, int limit) {
    if (typeOrNull == null || typeOrNull.isBlank()) {
      return jdbc.query(
          """
          SELECT id, target_type, target_id, source_type, source_id, browsed_at
          FROM bluedock_user_recent_items WHERE user_id = ?
          ORDER BY browsed_at DESC LIMIT ? OFFSET ?
          """,
          this::mapRow,
          userId,
          limit,
          offset);
    }
    return jdbc.query(
        """
        SELECT id, target_type, target_id, source_type, source_id, browsed_at
        FROM bluedock_user_recent_items WHERE user_id = ? AND target_type = ?
        ORDER BY browsed_at DESC LIMIT ? OFFSET ?
        """,
        this::mapRow,
        userId,
        typeOrNull,
        limit,
        offset);
  }

  public Optional<Long> findOwned(long userId, long id) {
    var list =
        jdbc.query(
            "SELECT id FROM bluedock_user_recent_items WHERE user_id = ? AND id = ? LIMIT 1",
            (rs, i) -> rs.getLong(1),
            userId,
            id);
    return list.stream().findFirst();
  }

  public void delete(long userId, long id) {
    jdbc.update("DELETE FROM bluedock_user_recent_items WHERE user_id = ? AND id = ?", userId, id);
  }

  private Map<String, Object> mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    Map<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("id", rs.getLong("id"));
    m.put("targetType", rs.getString("target_type"));
    m.put("targetId", rs.getLong("target_id"));
    m.put("sourceType", rs.getString("source_type"));
    m.put("sourceId", rs.getLong("source_id"));
    Timestamp b = rs.getTimestamp("browsed_at");
    m.put("browsedAt", b == null ? null : b.toLocalDateTime().toString());
    return m;
  }
}
