package com.bluedock.user.tag.repo;

import com.bluedock.common.util.IdGenerator;
import com.bluedock.user.tag.domain.UserTag;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserTagRepository {
  private static final RowMapper<UserTag> MAPPER = UserTagRepository::mapRow;

  private final JdbcTemplate jdbc;

  public UserTagRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(UserTag t) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_tags
          (id, user_id, creator_user_id, name, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        t.getId(),
        t.getUserId(),
        t.getCreatorUserId(),
        t.getName(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void updateName(long id, String name) {
    jdbc.update(
        """
        UPDATE bluedock_user_tags
        SET name = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        name,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  public void softDelete(long id) {
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    jdbc.update(
        "UPDATE bluedock_user_tags SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
        now,
        now,
        id);
    jdbc.update("DELETE FROM bluedock_user_tag_recognitions WHERE tag_id = ?", id);
  }

  public Optional<UserTag> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, user_id, creator_user_id, name, deleted_at
            FROM bluedock_user_tags
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<UserTag> listByUser(long userId) {
    return jdbc.query(
        """
        SELECT id, user_id, creator_user_id, name, deleted_at
        FROM bluedock_user_tags
        WHERE user_id = ? AND deleted_at IS NULL
        ORDER BY id DESC
        """,
        MAPPER,
        userId);
  }

  public Optional<UserTag> findByUserAndName(long userId, String name) {
    var list =
        jdbc.query(
            """
            SELECT id, user_id, creator_user_id, name, deleted_at
            FROM bluedock_user_tags
            WHERE user_id = ? AND name = ? AND deleted_at IS NULL
            """,
            MAPPER,
            userId,
            name);
    return list.stream().findFirst();
  }

  public int countByUser(long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_tags WHERE user_id = ? AND deleted_at IS NULL",
            Integer.class,
            userId);
    return n == null ? 0 : n;
  }

  public long countRecognitions(long tagId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_tag_recognitions WHERE tag_id = ?",
            Long.class,
            tagId);
    return n == null ? 0L : n;
  }

  public boolean hasRecognition(long tagId, long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_tag_recognitions WHERE tag_id = ? AND user_id = ?",
            Integer.class,
            tagId,
            userId);
    return n != null && n > 0;
  }

  public void insertRecognition(long tagId, long userId) {
    jdbc.update(
        """
        INSERT INTO bluedock_user_tag_recognitions (id, tag_id, user_id, created_at)
        VALUES (?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        tagId,
        userId,
        Timestamp.valueOf(LocalDateTime.now()));
  }

  public void deleteRecognition(long tagId, long userId) {
    jdbc.update(
        "DELETE FROM bluedock_user_tag_recognitions WHERE tag_id = ? AND user_id = ?",
        tagId,
        userId);
  }

  private static UserTag mapRow(ResultSet rs, int rowNum) throws SQLException {
    UserTag t = new UserTag();
    t.setId(rs.getLong("id"));
    t.setUserId(rs.getLong("user_id"));
    t.setCreatorUserId(rs.getLong("creator_user_id"));
    t.setName(rs.getString("name"));
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      t.setDeletedAt(deleted.toLocalDateTime());
    }
    return t;
  }
}
