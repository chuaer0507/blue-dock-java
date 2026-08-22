package com.bluedock.task.repo;

import com.bluedock.task.domain.TaskContent;
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
public class TaskContentRepository {
  private static final RowMapper<TaskContent> MAPPER = TaskContentRepository::mapRow;
  private static final RowMapper<TaskContent> META_MAPPER = TaskContentRepository::mapMeta;

  private final JdbcTemplate jdbc;

  public TaskContentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(TaskContent c) {
    jdbc.update(
        """
        INSERT INTO bluedock_task_contents
          (id, project_id, task_id, user_id, description, content, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        c.getId(),
        c.getProjectId(),
        c.getTaskId(),
        c.getUserId(),
        nullToEmpty(c.getDescription()),
        c.getContent(),
        toTs(c.getCreatedAt()),
        toTs(c.getUpdatedAt()));
  }

  public Optional<TaskContent> findLatest(long taskId) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, task_id, user_id, description, content, created_at, updated_at
            FROM bluedock_task_contents
            WHERE task_id = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            MAPPER,
            taskId);
    return list.stream().findFirst();
  }

  public Optional<TaskContent> findByIdAndTask(long id, long taskId) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, task_id, user_id, description, content, created_at, updated_at
            FROM bluedock_task_contents
            WHERE id = ? AND task_id = ?
            """,
            MAPPER,
            id,
            taskId);
    return list.stream().findFirst();
  }

  public long countByTask(long taskId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_task_contents WHERE task_id = ?", Long.class, taskId);
    return n == null ? 0L : n;
  }

  public List<TaskContent> listHistory(long taskId, int offset, int limit) {
    return jdbc.query(
        """
        SELECT id, project_id, task_id, user_id, description, NULL AS content, created_at, updated_at
        FROM bluedock_task_contents
        WHERE task_id = ?
        ORDER BY id DESC
        LIMIT ? OFFSET ?
        """,
        META_MAPPER,
        taskId,
        limit,
        offset);
  }

  private static TaskContent mapRow(ResultSet rs, int i) throws SQLException {
    TaskContent c = mapMeta(rs, i);
    c.setContent(rs.getString("content"));
    return c;
  }

  private static TaskContent mapMeta(ResultSet rs, int i) throws SQLException {
    TaskContent c = new TaskContent();
    c.setId(rs.getLong("id"));
    c.setProjectId(rs.getLong("project_id"));
    c.setTaskId(rs.getLong("task_id"));
    c.setUserId(rs.getLong("user_id"));
    c.setDescription(rs.getString("description"));
    Timestamp created = rs.getTimestamp("created_at");
    Timestamp updated = rs.getTimestamp("updated_at");
    if (created != null) {
      c.setCreatedAt(created.toLocalDateTime());
    }
    if (updated != null) {
      c.setUpdatedAt(updated.toLocalDateTime());
    }
    return c;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static Timestamp toTs(LocalDateTime v) {
    return v == null ? null : Timestamp.valueOf(v);
  }
}
