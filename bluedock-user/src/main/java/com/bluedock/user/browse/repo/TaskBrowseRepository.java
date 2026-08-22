package com.bluedock.user.browse.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaskBrowseRepository {
  private final JdbcTemplate jdbc;

  public TaskBrowseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void upsert(long userId, long taskId) {
    LocalDateTime now = LocalDateTime.now();
    int n =
        jdbc.update(
            """
            UPDATE bluedock_user_task_browses
            SET browsed_at = ?, updated_at = ?
            WHERE user_id = ? AND task_id = ?
            """,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now),
            userId,
            taskId);
    if (n == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_user_task_browses
            (id, user_id, task_id, browsed_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          userId,
          taskId,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }

  public List<Map<String, Object>> list(long userId, int limit) {
    return jdbc.query(
        """
        SELECT b.task_id, b.browsed_at, t.name, t.project_id, t.column_id, t.parent_id, t.complete_at
        FROM bluedock_user_task_browses b
        JOIN bluedock_tasks t ON t.id = b.task_id AND t.deleted_at IS NULL
        WHERE b.user_id = ?
        ORDER BY b.browsed_at DESC
        LIMIT ?
        """,
        (rs, i) -> {
          Map<String, Object> m = new java.util.LinkedHashMap<>();
          m.put("id", rs.getLong("task_id"));
          m.put("name", rs.getString("name"));
          m.put("projectId", rs.getLong("project_id"));
          m.put("columnId", rs.getLong("column_id"));
          m.put("parentId", rs.getLong("parent_id"));
          Timestamp c = rs.getTimestamp("complete_at");
          Timestamp b = rs.getTimestamp("browsed_at");
          m.put("completeAt", c == null ? null : c.toLocalDateTime().toString());
          m.put("browsedAt", b == null ? null : b.toLocalDateTime().toString());
          return m;
        },
        userId,
        limit);
  }

  public int clean(long userId, int keepCount) {
    if (keepCount <= 0) {
      return jdbc.update("DELETE FROM bluedock_user_task_browses WHERE user_id = ?", userId);
    }
    List<Long> keepIds =
        jdbc.query(
            """
            SELECT id FROM bluedock_user_task_browses
            WHERE user_id = ? ORDER BY browsed_at DESC LIMIT ?
            """,
            (rs, i) -> rs.getLong(1),
            userId,
            keepCount);
    if (keepIds.isEmpty()) {
      return jdbc.update("DELETE FROM bluedock_user_task_browses WHERE user_id = ?", userId);
    }
    String placeholders = String.join(",", keepIds.stream().map(x -> "?").toList());
    Object[] args = new Object[keepIds.size() + 1];
    args[0] = userId;
    for (int i = 0; i < keepIds.size(); i++) {
      args[i + 1] = keepIds.get(i);
    }
    return jdbc.update(
        "DELETE FROM bluedock_user_task_browses WHERE user_id = ? AND id NOT IN (" + placeholders + ")",
        args);
  }
}
