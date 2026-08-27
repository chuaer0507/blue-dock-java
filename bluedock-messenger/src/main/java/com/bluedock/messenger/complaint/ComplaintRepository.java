package com.bluedock.messenger.complaint;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ComplaintRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final RowMapper<Complaint> mapper;

  public ComplaintRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.mapper =
        (rs, i) -> {
          Complaint c = new Complaint();
          c.setId(rs.getLong("id"));
          c.setDialogId(rs.getLong("dialog_id"));
          c.setUserId(rs.getLong("user_id"));
          c.setType(rs.getInt("type"));
          c.setReason(rs.getString("reason"));
          c.setImages(parseImages(rs.getString("images")));
          c.setStatus(rs.getInt("status"));
          Timestamp ca = rs.getTimestamp("created_at");
          if (ca != null) {
            c.setCreatedAt(ca.toLocalDateTime());
          }
          Timestamp ua = rs.getTimestamp("updated_at");
          if (ua != null) {
            c.setUpdatedAt(ua.toLocalDateTime());
          }
          return c;
        };
  }

  public void insert(Complaint c) {
    jdbc.update(
        """
        INSERT INTO bluedock_complaints
          (id, dialog_id, user_id, type, reason, images, status, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        c.getId(),
        c.getDialogId(),
        c.getUserId(),
        c.getType(),
        c.getReason(),
        writeImages(c.getImages()),
        c.getStatus(),
        Timestamp.valueOf(c.getCreatedAt() == null ? LocalDateTime.now() : c.getCreatedAt()),
        Timestamp.valueOf(c.getUpdatedAt() == null ? LocalDateTime.now() : c.getUpdatedAt()));
  }

  public Optional<Complaint> findById(long id) {
    var list =
        jdbc.query("SELECT * FROM bluedock_complaints WHERE id = ? LIMIT 1", mapper, id);
    return list.stream().findFirst();
  }

  public long count(Integer type, Integer status) {
    StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM bluedock_complaints WHERE 1=1");
    List<Object> args = new ArrayList<>();
    appendFilters(sql, args, type, status);
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  public List<Complaint> page(Integer type, Integer status, int offset, int limit) {
    StringBuilder sql = new StringBuilder("SELECT * FROM bluedock_complaints WHERE 1=1");
    List<Object> args = new ArrayList<>();
    appendFilters(sql, args, type, status);
    sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(sql.toString(), mapper, args.toArray());
  }

  public void updateStatus(long id, int status) {
    jdbc.update(
        """
        UPDATE bluedock_complaints SET status = ?, updated_at = ? WHERE id = ?
        """,
        status,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  public void deleteById(long id) {
    jdbc.update("DELETE FROM bluedock_complaints WHERE id = ?", id);
  }

  /** 最近活跃的管理员用户 id（identity 含 admin）。 */
  public List<Long> listRecentAdminIds(int limit) {
    return jdbc.query(
        """
        SELECT id FROM bluedock_users
        WHERE disable_at IS NULL AND IFNULL(is_bot,0) = 0
          AND identity LIKE '%admin%'
        ORDER BY COALESCE(online_at, last_at, created_at) DESC
        LIMIT ?
        """,
        (rs, i) -> rs.getLong(1),
        Math.max(1, Math.min(limit, 20)));
  }

  private static void appendFilters(
      StringBuilder sql, List<Object> args, Integer type, Integer status) {
    if (type != null && type > 0) {
      sql.append(" AND type = ?");
      args.add(type);
    }
    if (status != null) {
      sql.append(" AND status = ?");
      args.add(status);
    }
  }

  private List<String> parseImages(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private String writeImages(List<String> images) {
    try {
      return objectMapper.writeValueAsString(images == null ? List.of() : images);
    } catch (Exception e) {
      return "[]";
    }
  }
}
