package com.bluedock.task.repo;

import com.bluedock.task.domain.TaskRelation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRelationRepository {
  private static final RowMapper<TaskRelation> MAPPER = TaskRelationRepository::mapRow;

  private final JdbcTemplate jdbc;

  public TaskRelationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<TaskRelation> listByTask(long taskId, int limit) {
    return jdbc.query(
        """
        SELECT id, task_id, related_task_id, direction, dialog_id, message_id, user_id, created_at, updated_at
        FROM bluedock_task_relations
        WHERE task_id = ?
        ORDER BY updated_at DESC, id DESC
        LIMIT ?
        """,
        MAPPER,
        taskId,
        limit);
  }

  public void upsert(
      long id,
      long taskId,
      long relatedTaskId,
      String direction,
      long dialogId,
      long messageId,
      long userId) {
    LocalDateTime now = LocalDateTime.now();
    int n =
        jdbc.update(
            """
            UPDATE bluedock_task_relations
            SET dialog_id = ?, message_id = ?, user_id = ?, updated_at = ?
            WHERE task_id = ? AND related_task_id = ? AND direction = ?
            """,
            dialogId,
            messageId,
            userId,
            Timestamp.valueOf(now),
            taskId,
            relatedTaskId,
            direction);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_task_relations
            (id, task_id, related_task_id, direction, dialog_id, message_id, user_id, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          id,
          taskId,
          relatedTaskId,
          direction,
          dialogId,
          messageId,
          userId,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public int deletePair(long taskId, long relatedTaskId) {
    return jdbc.update(
        """
        DELETE FROM bluedock_task_relations
        WHERE (task_id = ? AND related_task_id = ?)
           OR (task_id = ? AND related_task_id = ?)
        """,
        taskId,
        relatedTaskId,
        relatedTaskId,
        taskId);
  }

  private static TaskRelation mapRow(ResultSet rs, int i) throws SQLException {
    TaskRelation r = new TaskRelation();
    r.setId(rs.getLong("id"));
    r.setTaskId(rs.getLong("task_id"));
    r.setRelatedTaskId(rs.getLong("related_task_id"));
    r.setDirection(rs.getString("direction"));
    r.setDialogId(rs.getLong("dialog_id"));
    r.setMessageId(rs.getLong("message_id"));
    r.setUserId(rs.getLong("user_id"));
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    if (c != null) {
      r.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      r.setUpdatedAt(u.toLocalDateTime());
    }
    return r;
  }
}
