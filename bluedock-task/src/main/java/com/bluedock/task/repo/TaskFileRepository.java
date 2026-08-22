package com.bluedock.task.repo;

import com.bluedock.task.domain.TaskFile;
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
public class TaskFileRepository {
  private static final RowMapper<TaskFile> MAPPER = TaskFileRepository::mapRow;

  private final JdbcTemplate jdbc;

  public TaskFileRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(TaskFile f) {
    jdbc.update(
        """
        INSERT INTO bluedock_task_files
          (id, project_id, task_id, name, size, extension, path, thumbnail, user_id, download_count, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        f.getId(),
        f.getProjectId(),
        f.getTaskId(),
        f.getName(),
        f.getSize(),
        f.getExtension(),
        f.getPath(),
        f.getThumbnail(),
        f.getUserId(),
        f.getDownloadCount(),
        Timestamp.valueOf(f.getCreatedAt()),
        Timestamp.valueOf(f.getUpdatedAt()));
  }

  public List<TaskFile> listByTask(long taskId) {
    return jdbc.query(
        """
        SELECT id, project_id, task_id, name, size, extension, path, thumbnail, user_id, download_count, created_at, updated_at
        FROM bluedock_task_files
        WHERE task_id = ? AND deleted_at IS NULL
        ORDER BY id DESC
        LIMIT 50
        """,
        MAPPER,
        taskId);
  }

  public Optional<TaskFile> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, task_id, name, size, extension, path, thumbnail, user_id, download_count, created_at, updated_at
            FROM bluedock_task_files
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public void softDelete(long id) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_task_files SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now),
        id);
  }

  public void bumpDownload(long id) {
    jdbc.update(
        """
        UPDATE bluedock_task_files SET download_count = download_count + 1, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  private static TaskFile mapRow(ResultSet rs, int i) throws SQLException {
    TaskFile f = new TaskFile();
    f.setId(rs.getLong("id"));
    f.setProjectId(rs.getLong("project_id"));
    f.setTaskId(rs.getLong("task_id"));
    f.setName(rs.getString("name"));
    f.setSize(rs.getLong("size"));
    f.setExtension(rs.getString("extension") == null ? "" : rs.getString("extension"));
    f.setPath(rs.getString("path") == null ? "" : rs.getString("path"));
    f.setThumbnail(rs.getString("thumbnail") == null ? "" : rs.getString("thumbnail"));
    f.setUserId(rs.getLong("user_id"));
    f.setDownloadCount(rs.getInt("download_count"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    if (c != null) {
      f.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      f.setUpdatedAt(u.toLocalDateTime());
    }
    return f;
  }
}
