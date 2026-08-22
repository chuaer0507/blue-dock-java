package com.bluedock.file.repo;

import com.bluedock.file.domain.FileUser;
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
public class FileUserRepository {
  private static final RowMapper<FileUser> MAPPER = FileUserRepository::mapRow;

  private final JdbcTemplate jdbc;

  public FileUserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(FileUser u) {
    jdbc.update(
        """
        INSERT INTO bluedock_file_users
          (id, file_id, user_id, permission, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        u.getId(),
        u.getFileId(),
        u.getUserId(),
        u.getPermission(),
        Timestamp.valueOf(u.getCreatedAt()),
        Timestamp.valueOf(u.getUpdatedAt()));
  }

  public void updatePermission(long id, int permission, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_file_users SET permission = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        permission,
        Timestamp.valueOf(at),
        id);
  }

  public Optional<FileUser> findActive(long fileId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT id, file_id, user_id, permission, created_at, updated_at, deleted_at
            FROM bluedock_file_users
            WHERE file_id = ? AND user_id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            fileId,
            userId);
    return list.stream().findFirst();
  }

  public List<FileUser> listByFileId(long fileId) {
    return jdbc.query(
        """
        SELECT id, file_id, user_id, permission, created_at, updated_at, deleted_at
        FROM bluedock_file_users
        WHERE file_id = ? AND deleted_at IS NULL
        ORDER BY id ASC
        """,
        MAPPER,
        fileId);
  }

  public int countByFileId(long fileId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM bluedock_file_users
            WHERE file_id = ? AND deleted_at IS NULL
            """,
            Integer.class,
            fileId);
    return n == null ? 0 : n;
  }

  public void hardDelete(long fileId, long userId) {
    jdbc.update(
        """
        DELETE FROM bluedock_file_users WHERE file_id = ? AND user_id = ?
        """,
        fileId,
        userId);
  }

  public void softDeleteByFileId(long fileId, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_file_users SET deleted_at = ?, updated_at = ?
        WHERE file_id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(at),
        Timestamp.valueOf(at),
        fileId);
  }

  private static FileUser mapRow(ResultSet rs, int rowNum) throws SQLException {
    FileUser u = new FileUser();
    u.setId(rs.getLong("id"));
    u.setFileId(rs.getLong("file_id"));
    u.setUserId(rs.getLong("user_id"));
    u.setPermission(rs.getInt("permission"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp upd = rs.getTimestamp("updated_at");
    Timestamp d = rs.getTimestamp("deleted_at");
    if (c != null) {
      u.setCreatedAt(c.toLocalDateTime());
    }
    if (upd != null) {
      u.setUpdatedAt(upd.toLocalDateTime());
    }
    if (d != null) {
      u.setDeletedAt(d.toLocalDateTime());
    }
    return u;
  }
}
