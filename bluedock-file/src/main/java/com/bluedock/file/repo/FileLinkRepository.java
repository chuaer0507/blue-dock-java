package com.bluedock.file.repo;

import com.bluedock.file.domain.FileLink;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FileLinkRepository {
  private static final RowMapper<FileLink> MAPPER = FileLinkRepository::mapRow;

  private final JdbcTemplate jdbc;

  public FileLinkRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(FileLink link) {
    jdbc.update(
        """
        INSERT INTO bluedock_file_links
          (id, file_id, code, permission, allow_guest, user_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        link.getId(),
        link.getFileId(),
        link.getCode(),
        link.getPermission(),
        link.getAllowGuest(),
        link.getUserId(),
        Timestamp.valueOf(link.getCreatedAt()),
        Timestamp.valueOf(link.getUpdatedAt()));
  }

  public Optional<FileLink> findActiveByFileId(long fileId) {
    var list =
        jdbc.query(
            """
            SELECT id, file_id, code, permission, allow_guest, user_id, created_at, updated_at, deleted_at
            FROM bluedock_file_links
            WHERE file_id = ? AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """,
            MAPPER,
            fileId);
    return list.stream().findFirst();
  }

  public Optional<FileLink> findActiveByCode(String code) {
    var list =
        jdbc.query(
            """
            SELECT id, file_id, code, permission, allow_guest, user_id, created_at, updated_at, deleted_at
            FROM bluedock_file_links
            WHERE code = ? AND deleted_at IS NULL
            LIMIT 1
            """,
            MAPPER,
            code);
    return list.stream().findFirst();
  }

  public void softDeleteByFileId(long fileId, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_file_links SET deleted_at = ?, updated_at = ?
        WHERE file_id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(at),
        Timestamp.valueOf(at),
        fileId);
  }

  public void updateMeta(long id, int permission, int allowGuest, LocalDateTime at) {
    jdbc.update(
        """
        UPDATE bluedock_file_links SET permission = ?, allow_guest = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        permission,
        allowGuest,
        Timestamp.valueOf(at),
        id);
  }

  private static FileLink mapRow(ResultSet rs, int rowNum) throws SQLException {
    FileLink link = new FileLink();
    link.setId(rs.getLong("id"));
    link.setFileId(rs.getLong("file_id"));
    link.setCode(rs.getString("code"));
    link.setPermission(rs.getInt("permission"));
    link.setAllowGuest(rs.getInt("allow_guest"));
    link.setUserId(rs.getLong("user_id"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    Timestamp d = rs.getTimestamp("deleted_at");
    if (c != null) {
      link.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      link.setUpdatedAt(u.toLocalDateTime());
    }
    if (d != null) {
      link.setDeletedAt(d.toLocalDateTime());
    }
    return link;
  }
}
