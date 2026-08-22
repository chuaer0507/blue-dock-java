package com.bluedock.file.repo;

import com.bluedock.file.domain.FileContent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FileContentRepository {
  private static final RowMapper<FileContent> MAPPER = FileContentRepository::mapRow;

  private final JdbcTemplate jdbc;

  public FileContentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(FileContent c) {
    jdbc.update(
        """
            INSERT INTO bluedock_file_contents
              (id, file_id, content, text, size, user_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
        c.getId(),
        c.getFileId(),
        c.getContent(),
        c.getText(),
        c.getSize(),
        c.getUserId(),
        Timestamp.valueOf(c.getCreatedAt()),
        Timestamp.valueOf(c.getUpdatedAt()));
  }

  public Optional<FileContent> findLatest(long fileId) {
    var list = jdbc.query(
        """
            SELECT id, file_id, content, text, size, user_id, created_at, updated_at, deleted_at
            FROM bluedock_file_contents
            WHERE file_id = ? AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT 1
            """,
        MAPPER,
        fileId);
    return list.stream().findFirst();
  }

  public Optional<FileContent> findActive(long id) {
    var list = jdbc.query(
        """
            SELECT id, file_id, content, text, size, user_id, created_at, updated_at, deleted_at
            FROM bluedock_file_contents
            WHERE id = ? AND deleted_at IS NULL
            """,
        MAPPER,
        id);
    return list.stream().findFirst();
  }

  public List<FileContent> listHistory(long fileId, int limit) {
    return jdbc.query(
        """
            SELECT id, file_id, content, text, size, user_id, created_at, updated_at, deleted_at
            FROM bluedock_file_contents
            WHERE file_id = ? AND deleted_at IS NULL
            ORDER BY id DESC
            LIMIT ?
            """,
        MAPPER,
        fileId,
        limit);
  }

  private static FileContent mapRow(ResultSet rs, int rowNum) throws SQLException {
    FileContent c = new FileContent();
    c.setId(rs.getLong("id"));
    c.setFileId(rs.getLong("file_id"));
    c.setContent(rs.getString("content"));
    c.setText(rs.getString("text"));
    c.setSize(rs.getLong("size"));
    c.setUserId(rs.getLong("user_id"));
    Timestamp created = rs.getTimestamp("created_at");
    Timestamp updated = rs.getTimestamp("updated_at");
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (created != null) {
      c.setCreatedAt(created.toLocalDateTime());
    }
    if (updated != null) {
      c.setUpdatedAt(updated.toLocalDateTime());
    }
    if (deleted != null) {
      c.setDeletedAt(deleted.toLocalDateTime());
    }
    return c;
  }
}
