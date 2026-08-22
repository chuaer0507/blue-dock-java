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
public class TaskTagRepository {
  private final JdbcTemplate jdbc;

  public TaskTagRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Long> listTagIds(long taskId) {
    return jdbc.query(
        "SELECT tag_id FROM bluedock_task_tags WHERE task_id = ? ORDER BY id ASC",
        (rs, i) -> rs.getLong(1),
        taskId);
  }

  public Map<Long, List<Long>> listTagIdsByTaskIds(Collection<Long> taskIds) {
    Map<Long, List<Long>> out = new LinkedHashMap<>();
    if (taskIds == null || taskIds.isEmpty()) {
      return out;
    }
    for (Long id : taskIds) {
      out.put(id, new ArrayList<>());
    }
    String in = taskIds.stream().map(x -> "?").collect(Collectors.joining(","));
    jdbc.query(
        "SELECT task_id, tag_id FROM bluedock_task_tags WHERE task_id IN (" + in + ") ORDER BY id ASC",
        rs -> {
          out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
        },
        taskIds.toArray());
    return out;
  }

  public void replace(long taskId, long projectId, Collection<Long> tagIds) {
    jdbc.update("DELETE FROM bluedock_task_tags WHERE task_id = ?", taskId);
    if (tagIds == null || tagIds.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    Timestamp ts = Timestamp.valueOf(now);
    for (Long tagId : tagIds) {
      if (tagId == null || tagId <= 0) {
        continue;
      }
      jdbc.update(
          """
          INSERT INTO bluedock_task_tags (id, project_id, task_id, tag_id, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          projectId,
          taskId,
          tagId,
          ts,
          ts);
    }
  }

  public void deleteByTask(long taskId) {
    jdbc.update("DELETE FROM bluedock_task_tags WHERE task_id = ?", taskId);
  }

  public void deleteByTaskAndProject(long taskId) {
    deleteByTask(taskId);
  }

  public void updateProjectForTask(long taskId, long projectId) {
    jdbc.update(
        "UPDATE bluedock_task_tags SET project_id = ?, updated_at = ? WHERE task_id = ?",
        projectId,
        Timestamp.valueOf(LocalDateTime.now()),
        taskId);
  }
}
