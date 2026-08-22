package com.bluedock.user.device.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserDeviceRepository {
  private final JdbcTemplate jdbc;

  public UserDeviceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long insert(long userId, String hash, String detailJson, LocalDateTime expiredAt) {
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_devices
          (id, user_id, hash, detail, expired_at, created_at, updated_at, deleted_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
        """,
        id,
        userId,
        hash,
        detailJson,
        expiredAt == null ? null : Timestamp.valueOf(expiredAt),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return id;
  }

  public List<Map<String, Object>> listActive(long userId, int limit) {
    return jdbc.query(
        """
        SELECT id, user_id, hash, detail, expired_at, created_at, updated_at
        FROM bluedock_user_devices
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY id DESC
        LIMIT ?
        """,
        (rs, i) -> {
          Map<String, Object> row = new java.util.LinkedHashMap<>();
          row.put("id", rs.getLong("id"));
          row.put("userId", rs.getLong("user_id"));
          row.put("hash", rs.getString("hash"));
          row.put("detail", rs.getString("detail"));
          Timestamp exp = rs.getTimestamp("expired_at");
          Timestamp c = rs.getTimestamp("created_at");
          Timestamp u = rs.getTimestamp("updated_at");
          row.put("expiredAt", exp == null ? null : exp.toLocalDateTime().toString());
          row.put("createdAt", c == null ? null : c.toLocalDateTime().toString());
          row.put("updatedAt", u == null ? null : u.toLocalDateTime().toString());
          return row;
        },
        userId,
        limit);
  }

  public int countActive(long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_devices WHERE user_id = ? AND deleted_at IS NULL",
            Integer.class,
            userId);
    return n == null ? 0 : n;
  }

  public Optional<Map<String, Object>> findActive(long userId, long id) {
    var list =
        jdbc.query(
            """
            SELECT id, user_id, hash, detail FROM bluedock_user_devices
            WHERE user_id = ? AND id = ? AND deleted_at IS NULL
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> row = new java.util.LinkedHashMap<>();
              row.put("id", rs.getLong("id"));
              row.put("userId", rs.getLong("user_id"));
              row.put("hash", rs.getString("hash"));
              row.put("detail", rs.getString("detail"));
              return row;
            },
            userId,
            id);
    return list.stream().findFirst();
  }

  public Optional<Map<String, Object>> findByHash(long userId, String hash) {
    var list =
        jdbc.query(
            """
            SELECT id, user_id, hash, detail FROM bluedock_user_devices
            WHERE user_id = ? AND hash = ? AND deleted_at IS NULL
            LIMIT 1
            """,
            (rs, i) -> {
              Map<String, Object> row = new java.util.LinkedHashMap<>();
              row.put("id", rs.getLong("id"));
              row.put("userId", rs.getLong("user_id"));
              row.put("hash", rs.getString("hash"));
              row.put("detail", rs.getString("detail"));
              return row;
            },
            userId,
            hash);
    return list.stream().findFirst();
  }

  public void softDelete(long id) {
    jdbc.update(
        """
        UPDATE bluedock_user_devices SET deleted_at = ?, updated_at = ? WHERE id = ?
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  public void updateDetail(long id, String detailJson) {
    jdbc.update(
        """
        UPDATE bluedock_user_devices SET detail = ?, updated_at = ? WHERE id = ?
        """,
        detailJson,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  public void pruneOldest(long userId, int keep) {
    List<Long> ids =
        jdbc.query(
            """
            SELECT id FROM bluedock_user_devices
            WHERE user_id = ? AND deleted_at IS NULL
            ORDER BY id DESC
            """,
            (rs, i) -> rs.getLong(1),
            userId);
    if (ids.size() <= keep) {
      return;
    }
    for (int i = keep; i < ids.size(); i++) {
      softDelete(ids.get(i));
    }
  }
}
