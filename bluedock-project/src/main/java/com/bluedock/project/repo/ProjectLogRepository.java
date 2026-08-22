package com.bluedock.project.repo;

import com.bluedock.project.domain.ProjectLog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectLogRepository {
  private final JdbcTemplate jdbc;

  public ProjectLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<ProjectLog> findById(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, column_id, task_id, task_only, user_id, detail, record,
                   created_at, updated_at
            FROM bluedock_project_logs
            WHERE id = ?
            LIMIT 1
            """,
            this::mapRow,
            id);
    return list.stream().findFirst();
  }

  public void insert(ProjectLog log) {
    LocalDateTime now = log.getCreatedAt() != null ? log.getCreatedAt() : LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_project_logs
          (id, project_id, column_id, task_id, task_only, user_id, detail, record, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        log.getId(),
        log.getProjectId(),
        log.getColumnId(),
        log.getTaskId(),
        log.getTaskOnly(),
        log.getUserId(),
        log.getDetail(),
        log.getRecordJson(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public long countByProject(long projectId) {
    Long n =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM bluedock_project_logs
            WHERE project_id = ? AND task_only = 0
            """,
            Long.class,
            projectId);
    return n == null ? 0L : n;
  }

  public long countByTask(long taskId) {
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM bluedock_project_logs WHERE task_id = ?", Long.class, taskId);
    return n == null ? 0L : n;
  }

  public List<ProjectLog> listByProject(long projectId, int offset, int limit) {
    return jdbc.query(
        """
        SELECT id, project_id, column_id, task_id, task_only, user_id, detail, record,
               created_at, updated_at
        FROM bluedock_project_logs
        WHERE project_id = ? AND task_only = 0
        ORDER BY created_at DESC, id DESC
        LIMIT ? OFFSET ?
        """,
        this::mapRow,
        projectId,
        limit,
        offset);
  }

  public List<ProjectLog> listByTask(long taskId, int offset, int limit) {
    return jdbc.query(
        """
        SELECT id, project_id, column_id, task_id, task_only, user_id, detail, record,
               created_at, updated_at
        FROM bluedock_project_logs
        WHERE task_id = ?
        ORDER BY created_at DESC, id DESC
        LIMIT ? OFFSET ?
        """,
        this::mapRow,
        taskId,
        limit,
        offset);
  }

  /** 读任务所属项目（跨表轻量查询，供日志鉴权）。 */
  public Optional<Long> findTaskProjectId(long taskId) {
    var list =
        jdbc.query(
            """
            SELECT project_id FROM bluedock_tasks
            WHERE id = ? AND deleted_at IS NULL
            """,
            (rs, i) -> rs.getLong(1),
            taskId);
    return list.stream().findFirst();
  }

  public Optional<Map<String, Object>> findTaskBrief(long taskId) {
    var list =
        jdbc.query(
            """
            SELECT id, parent_id, name FROM bluedock_tasks
            WHERE id = ? AND deleted_at IS NULL
            """,
            (rs, i) ->
                Map.<String, Object>of(
                    "id", rs.getLong("id"),
                    "parentId", rs.getLong("parent_id"),
                    "name", rs.getString("name") == null ? "" : rs.getString("name")),
            taskId);
    return list.stream().findFirst();
  }

  private ProjectLog mapRow(ResultSet rs, int rowNum) throws SQLException {
    ProjectLog log = new ProjectLog();
    log.setId(rs.getLong("id"));
    log.setProjectId(rs.getLong("project_id"));
    log.setColumnId(rs.getLong("column_id"));
    log.setTaskId(rs.getLong("task_id"));
    log.setTaskOnly(rs.getInt("task_only"));
    log.setUserId(rs.getLong("user_id"));
    log.setDetail(rs.getString("detail") == null ? "" : rs.getString("detail"));
    log.setRecordJson(rs.getString("record"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    if (c != null) {
      log.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      log.setUpdatedAt(u.toLocalDateTime());
    }
    return log;
  }
}
