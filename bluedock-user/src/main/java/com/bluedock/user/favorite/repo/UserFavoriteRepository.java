package com.bluedock.user.favorite.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserFavoriteRepository {
  private final JdbcTemplate jdbc;

  public UserFavoriteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Long> findId(long userId, String type, long refId) {
    var list =
        jdbc.query(
            """
            SELECT id FROM bluedock_user_favorites
            WHERE user_id = ? AND fav_type = ? AND ref_id = ?
            LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            userId,
            type,
            refId);
    return list.stream().findFirst();
  }

  public void insert(long userId, String type, long refId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_favorites
          (id, user_id, fav_type, ref_id, remark, created_at, updated_at)
        VALUES (?, ?, ?, ?, '', ?, ?)
        """,
        IdGenerator.nextId(),
        userId,
        type,
        refId,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void delete(long userId, String type, long refId) {
    jdbc.update(
        "DELETE FROM bluedock_user_favorites WHERE user_id = ? AND fav_type = ? AND ref_id = ?",
        userId,
        type,
        refId);
  }

  public void updateRemark(long userId, String type, long refId, String remark) {
    jdbc.update(
        """
        UPDATE bluedock_user_favorites SET remark = ?, updated_at = ?
        WHERE user_id = ? AND fav_type = ? AND ref_id = ?
        """,
        remark,
        Timestamp.valueOf(LocalDateTime.now()),
        userId,
        type,
        refId);
  }

  public int deleteByUser(long userId, String typeOrNull) {
    if (typeOrNull == null || typeOrNull.isBlank()) {
      return jdbc.update("DELETE FROM bluedock_user_favorites WHERE user_id = ?", userId);
    }
    return jdbc.update(
        "DELETE FROM bluedock_user_favorites WHERE user_id = ? AND fav_type = ?", userId, typeOrNull);
  }

  public List<Map<String, Object>> page(long userId, String typeOrNull, int offset, int limit) {
    if (typeOrNull == null || typeOrNull.isBlank()) {
      return jdbc.query(
          """
          SELECT id, fav_type, ref_id, remark, created_at, updated_at
          FROM bluedock_user_favorites WHERE user_id = ?
          ORDER BY id DESC LIMIT ? OFFSET ?
          """,
          this::mapRow,
          userId,
          limit,
          offset);
    }
    return jdbc.query(
        """
        SELECT id, fav_type, ref_id, remark, created_at, updated_at
        FROM bluedock_user_favorites WHERE user_id = ? AND fav_type = ?
        ORDER BY id DESC LIMIT ? OFFSET ?
        """,
        this::mapRow,
        userId,
        typeOrNull,
        limit,
        offset);
  }

  public int count(long userId, String typeOrNull) {
    Integer n;
    if (typeOrNull == null || typeOrNull.isBlank()) {
      n =
          jdbc.queryForObject(
              "SELECT COUNT(1) FROM bluedock_user_favorites WHERE user_id = ?", Integer.class, userId);
    } else {
      n =
          jdbc.queryForObject(
              "SELECT COUNT(1) FROM bluedock_user_favorites WHERE user_id = ? AND fav_type = ?",
              Integer.class,
              userId,
              typeOrNull);
    }
    return n == null ? 0 : n;
  }

  private Map<String, Object> mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    Map<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("id", rs.getLong("id"));
    m.put("type", rs.getString("fav_type"));
    m.put("refId", rs.getLong("ref_id"));
    m.put("remark", rs.getString("remark") == null ? "" : rs.getString("remark"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    m.put("createdAt", c == null ? null : c.toLocalDateTime().toString());
    m.put("updatedAt", u == null ? null : u.toLocalDateTime().toString());
    return m;
  }
}
