package com.bluedock.task.repo;

import com.bluedock.common.util.IdGenerator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TaskVisibilityUserRepository {
  private final JdbcTemplate jdbc;

  public TaskVisibilityUserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Long> listUserIds(long taskId) {
    return jdbc.query(
        "SELECT user_id FROM bluedock_task_visibility_users WHERE task_id = ? ORDER BY id ASC",
        (rs, i) -> rs.getLong(1),
        taskId);
  }

  public Map<Long, List<Long>> listUserIdsByTaskIds(Collection<Long> taskIds) {
    Map<Long, List<Long>> out = new LinkedHashMap<>();
    if (taskIds == null || taskIds.isEmpty()) {
      return out;
    }
    for (Long id : taskIds) {
      out.put(id, new ArrayList<>());
    }
    String in = taskIds.stream().map(x -> "?").collect(Collectors.joining(","));
    jdbc.query(
        "SELECT task_id, user_id FROM bluedock_task_visibility_users WHERE task_id IN ("
            + in
            + ") ORDER BY id ASC",
        rs -> {
          out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
        },
        taskIds.toArray());
    return out;
  }

  public boolean exists(long taskId, long userId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_task_visibility_users WHERE task_id = ? AND user_id = ?",
            Integer.class,
            taskId,
            userId);
    return n != null && n > 0;
  }

  /** 全量替换指定成员；{@code userIds} 空则清空。 */
  public void replace(long taskId, long projectId, Collection<Long> userIds) {
    jdbc.update("DELETE FROM bluedock_task_visibility_users WHERE task_id = ?", taskId);
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    Timestamp ts = Timestamp.valueOf(now);
    for (Long userId : userIds) {
      if (userId == null || userId <= 0) {
        continue;
      }
      jdbc.update(
          """
          INSERT INTO bluedock_task_visibility_users
            (id, task_id, project_id, user_id, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          taskId,
          projectId,
          userId,
          ts,
          ts);
    }
  }

  public void deleteByTask(long taskId) {
    jdbc.update("DELETE FROM bluedock_task_visibility_users WHERE task_id = ?", taskId);
  }

  public void deleteByUser(long userId) {
    jdbc.update("DELETE FROM bluedock_task_visibility_users WHERE user_id = ?", userId);
  }
}
