package com.bluedock.task.repo;

import com.bluedock.task.domain.TaskAiEvent;
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
public class TaskAiEventRepository {
  private static final RowMapper<TaskAiEvent> MAPPER = TaskAiEventRepository::mapRow;

  private final JdbcTemplate jdbc;

  public TaskAiEventRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(TaskAiEvent e) {
    jdbc.update(
        """
        INSERT INTO bluedock_task_ai_events
          (id, task_id, event_type, status, retry_count, result, error, message_id,
           executed_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?)
        """,
        e.getId(),
        e.getTaskId(),
        e.getEventType(),
        e.getStatus(),
        e.getRetryCount(),
        e.getResultJson(),
        e.getError(),
        e.getMessageId(),
        toTs(e.getExecutedAt()),
        toTs(e.getCreatedAt()),
        toTs(e.getUpdatedAt()));
  }

  public Optional<TaskAiEvent> findByTaskAndType(long taskId, String eventType) {
    var list =
        jdbc.query(
            """
            SELECT id, task_id, event_type, status, retry_count, result, error, message_id,
                   executed_at, created_at, updated_at
            FROM bluedock_task_ai_events
            WHERE task_id = ? AND event_type = ?
            LIMIT 1
            """,
            MAPPER,
            taskId,
            eventType);
    return list.stream().findFirst();
  }

  public Optional<TaskAiEvent> findByTaskTypeMessage(long taskId, String eventType, long messageId) {
    var list =
        jdbc.query(
            """
            SELECT id, task_id, event_type, status, retry_count, result, error, message_id,
                   executed_at, created_at, updated_at
            FROM bluedock_task_ai_events
            WHERE task_id = ? AND event_type = ? AND message_id = ?
            LIMIT 1
            """,
            MAPPER,
            taskId,
            eventType,
            messageId);
    return list.stream().findFirst();
  }

  public List<TaskAiEvent> listByTask(long taskId) {
    return jdbc.query(
        """
        SELECT id, task_id, event_type, status, retry_count, result, error, message_id,
               executed_at, created_at, updated_at
        FROM bluedock_task_ai_events
        WHERE task_id = ?
        ORDER BY id ASC
        """,
        MAPPER,
        taskId);
  }

  /** 尚无任何 AI 事件的新建主任务。 */
  public List<Long> listNewTasksWithoutAiEvents(
      LocalDateTime createdBefore, LocalDateTime createdAfter, int limit) {
    int take = Math.min(Math.max(limit, 1), 20);
    return jdbc.query(
        """
        SELECT t.id
        FROM bluedock_tasks t
        WHERE t.parent_id = 0
          AND t.deleted_at IS NULL
          AND t.archived_at IS NULL
          AND t.created_at <= ?
          AND t.created_at >= ?
          AND NOT EXISTS (SELECT 1 FROM bluedock_task_ai_events e WHERE e.task_id = t.id)
        ORDER BY t.created_at ASC
        LIMIT ?
        """,
        (rs, i) -> rs.getLong("id"),
        toTs(createdBefore),
        toTs(createdAfter),
        take);
  }

  /** 有可重试 failed 事件的任务。 */
  public List<Long> listRetryableFailedTaskIds(int limit) {
    int take = Math.min(Math.max(limit, 1), 20);
    return jdbc.query(
        """
        SELECT e.task_id AS id
        FROM bluedock_task_ai_events e
        INNER JOIN bluedock_tasks t ON t.id = e.task_id
          AND t.parent_id = 0 AND t.deleted_at IS NULL AND t.archived_at IS NULL
        WHERE e.status = ?
          AND e.retry_count < ?
        GROUP BY e.task_id
        ORDER BY MIN(e.updated_at) ASC
        LIMIT ?
        """,
        (rs, i) -> rs.getLong("id"),
        TaskAiEvent.STATUS_FAILED,
        TaskAiEvent.MAX_RETRY,
        take);
  }

  public boolean markProcessing(long id) {
    int n =
        jdbc.update(
            """
            UPDATE bluedock_task_ai_events
            SET status = ?, updated_at = ?
            WHERE id = ? AND status IN (?, ?)
            """,
            TaskAiEvent.STATUS_PROCESSING,
            toTs(LocalDateTime.now()),
            id,
            TaskAiEvent.STATUS_PENDING,
            TaskAiEvent.STATUS_FAILED);
    return n > 0;
  }

  public void markCompleted(long id, String resultJson, long messageId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_task_ai_events
        SET status = ?, result = CAST(? AS JSON), error = NULL, message_id = ?,
            executed_at = ?, updated_at = ?
        WHERE id = ?
        """,
        TaskAiEvent.STATUS_COMPLETED,
        resultJson,
        messageId,
        toTs(now),
        toTs(now),
        id);
  }

  public void markSkipped(long id, String reason) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_task_ai_events
        SET status = ?, error = ?, executed_at = ?, updated_at = ?
        WHERE id = ?
        """,
        TaskAiEvent.STATUS_SKIPPED,
        reason == null ? "" : reason,
        toTs(now),
        toTs(now),
        id);
  }

  public void markFailed(long id, String error, int retryCount) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_task_ai_events
        SET status = ?, error = ?, retry_count = ?, executed_at = ?, updated_at = ?
        WHERE id = ?
        """,
        TaskAiEvent.STATUS_FAILED,
        error == null ? "" : error,
        retryCount,
        toTs(now),
        toTs(now),
        id);
  }

  public void markStatus(long id, String status) {
    jdbc.update(
        """
        UPDATE bluedock_task_ai_events
        SET status = ?, updated_at = ?
        WHERE id = ?
        """,
        status,
        toTs(LocalDateTime.now()),
        id);
  }

  public void updateMessageIdForCompleted(long taskId, long messageId) {
    jdbc.update(
        """
        UPDATE bluedock_task_ai_events
        SET message_id = ?, updated_at = ?
        WHERE task_id = ? AND status = ? AND (message_id = 0 OR message_id IS NULL)
        """,
        messageId,
        toTs(LocalDateTime.now()),
        taskId,
        TaskAiEvent.STATUS_COMPLETED);
  }

  public List<MemberLoad> listProjectMemberLoads(long projectId, List<Long> excludeUserIds) {
    String exclude =
        excludeUserIds == null || excludeUserIds.isEmpty()
            ? "0"
            : excludeUserIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("0");
    String sql =
        """
        SELECT u.id AS userId,
               COALESCE(NULLIF(TRIM(u.nickname), ''), u.email) AS nickname,
               COALESCE(u.profession, '') AS profession,
               (SELECT COUNT(1) FROM bluedock_tasks t
                  INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = u.id
                 WHERE t.project_id = ? AND t.deleted_at IS NULL AND t.archived_at IS NULL
                   AND t.complete_at IS NULL) AS in_progress,
               (SELECT COUNT(1) FROM bluedock_tasks t
                  INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = u.id
                 WHERE t.project_id = ? AND t.deleted_at IS NULL
                   AND t.complete_at IS NOT NULL
                   AND t.complete_at >= (UTC_TIMESTAMP(3) - INTERVAL 30 DAY)) AS completed_recent
        FROM bluedock_project_users pu
        INNER JOIN bluedock_users u ON u.id = pu.user_id
        WHERE pu.project_id = ?
          AND IFNULL(u.is_bot, 0) = 0
          AND u.disable_at IS NULL
          AND u.id NOT IN (%s)
        ORDER BY in_progress ASC, completed_recent DESC, u.id ASC
        LIMIT 20
        """
            .formatted(exclude);
    return jdbc.query(
        sql,
        (rs, i) ->
            new MemberLoad(
                rs.getLong("userId"),
                rs.getString("nickname"),
                rs.getString("profession"),
                rs.getInt("in_progress"),
                rs.getInt("completed_recent")),
        projectId,
        projectId,
        projectId);
  }

  public List<SimilarTask> findSimilarByName(long projectId, long excludeTaskId, String name, int limit) {
    String keyword = name == null ? "" : name.trim();
    if (keyword.length() < 2) {
      return List.of();
    }
    String like = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    return jdbc.query(
        """
        SELECT id, name
        FROM bluedock_tasks
        WHERE project_id = ?
          AND id <> ?
          AND parent_id = 0
          AND deleted_at IS NULL
          AND name LIKE ? ESCAPE '!'
        ORDER BY id DESC
        LIMIT ?
        """,
        (rs, i) -> new SimilarTask(rs.getLong("id"), rs.getString("name"), 0.6),
        projectId,
        excludeTaskId,
        like,
        limit);
  }

  public record MemberLoad(
      long userId, String nickname, String profession, int inProgress, int completedRecent) {}

  public record SimilarTask(long taskId, String name, double similarity) {}

  private static TaskAiEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    TaskAiEvent e = new TaskAiEvent();
    e.setId(rs.getLong("id"));
    e.setTaskId(rs.getLong("task_id"));
    e.setEventType(rs.getString("event_type"));
    e.setStatus(rs.getString("status"));
    e.setRetryCount(rs.getInt("retry_count"));
    e.setResultJson(rs.getString("result"));
    e.setError(rs.getString("error"));
    e.setMessageId(rs.getLong("message_id"));
    Timestamp ex = rs.getTimestamp("executed_at");
    if (ex != null) {
      e.setExecutedAt(ex.toLocalDateTime());
    }
    Timestamp c = rs.getTimestamp("created_at");
    if (c != null) {
      e.setCreatedAt(c.toLocalDateTime());
    }
    Timestamp u = rs.getTimestamp("updated_at");
    if (u != null) {
      e.setUpdatedAt(u.toLocalDateTime());
    }
    return e;
  }

  private static Timestamp toTs(LocalDateTime t) {
    return t == null ? null : Timestamp.valueOf(t);
  }
}
