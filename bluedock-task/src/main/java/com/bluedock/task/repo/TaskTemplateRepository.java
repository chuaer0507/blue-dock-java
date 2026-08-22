package com.bluedock.task.repo;

import com.bluedock.task.domain.TaskTemplate;
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
public class TaskTemplateRepository {
  private static final RowMapper<TaskTemplate> MAPPER = TaskTemplateRepository::mapRow;

  private final JdbcTemplate jdbc;

  public TaskTemplateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(TaskTemplate t) {
    jdbc.update(
        """
        INSERT INTO bluedock_task_templates
          (id, project_id, name, title, content, sort, is_default, user_id, use_count,
           last_used_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        t.getId(),
        t.getProjectId(),
        t.getName(),
        nullToEmpty(t.getTitle()),
        nullToEmpty(t.getContent()),
        t.getSort(),
        t.getIsDefault(),
        t.getUserId(),
        t.getUseCount(),
        toTs(t.getLastUsedAt()),
        toTs(t.getCreatedAt()),
        toTs(t.getUpdatedAt()));
  }

  public void update(TaskTemplate t) {
    jdbc.update(
        """
        UPDATE bluedock_task_templates
        SET name = ?, title = ?, content = ?, updated_at = ?
        WHERE id = ? AND project_id = ?
        """,
        t.getName(),
        nullToEmpty(t.getTitle()),
        nullToEmpty(t.getContent()),
        Timestamp.valueOf(LocalDateTime.now()),
        t.getId(),
        t.getProjectId());
  }

  public Optional<TaskTemplate> find(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, name, title, content, sort, is_default, user_id, use_count,
                   last_used_at, created_at, updated_at
            FROM bluedock_task_templates WHERE id = ?
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<TaskTemplate> listByProject(long projectId) {
    return jdbc.query(
        """
        SELECT id, project_id, name, title, content, sort, is_default, user_id, use_count,
               last_used_at, created_at, updated_at
        FROM bluedock_task_templates
        WHERE project_id = ?
        ORDER BY sort ASC, id DESC
        """,
        MAPPER,
        projectId);
  }

  public List<TaskTemplate> listByProjects(List<Long> projectIds, long preferProjectId) {
    if (projectIds == null || projectIds.isEmpty()) {
      return List.of();
    }
    String in = projectIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    return jdbc.query(
        """
        SELECT t.id, t.project_id, t.name, t.title, t.content, t.sort, t.is_default, t.user_id,
               t.use_count, t.last_used_at, t.created_at, t.updated_at, p.name AS project_name
        FROM bluedock_task_templates t
        INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
        WHERE t.project_id IN (
        """
            + in
            + ") ORDER BY (t.project_id = ?) DESC, t.sort ASC, t.id ASC",
        (rs, i) -> {
          TaskTemplate t = mapRow(rs, i);
          t.setProjectName(rs.getString("project_name"));
          return t;
        },
        buildArgs(preferProjectId, projectIds));
  }

  public int countByProject(long projectId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_task_templates WHERE project_id = ?",
            Integer.class,
            projectId);
    return n == null ? 0 : n;
  }

  public int nextSort(long projectId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(sort), -1) + 1 FROM bluedock_task_templates WHERE project_id = ?",
            Integer.class,
            projectId);
    return n == null ? 0 : n;
  }

  public void updateSort(long id, long projectId, int sort) {
    jdbc.update(
        "UPDATE bluedock_task_templates SET sort = ?, updated_at = ? WHERE id = ? AND project_id = ?",
        sort,
        Timestamp.valueOf(LocalDateTime.now()),
        id,
        projectId);
  }

  public void clearDefault(long projectId) {
    jdbc.update(
        "UPDATE bluedock_task_templates SET is_default = 0, updated_at = ? WHERE project_id = ?",
        Timestamp.valueOf(LocalDateTime.now()),
        projectId);
  }

  public void setDefault(long id, long projectId, boolean isDefault) {
    jdbc.update(
        "UPDATE bluedock_task_templates SET is_default = ?, updated_at = ? WHERE id = ? AND project_id = ?",
        isDefault ? 1 : 0,
        Timestamp.valueOf(LocalDateTime.now()),
        id,
        projectId);
  }

  public void delete(long id) {
    jdbc.update("DELETE FROM bluedock_task_templates WHERE id = ?", id);
  }

  public void incrementUsage(long id) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_task_templates
        SET use_count = use_count + 1, last_used_at = ?, updated_at = ?
        WHERE id = ?
        """,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now),
        id);
  }

  public long countSearch(List<Long> projectIds, String keyword) {
    if (projectIds == null || projectIds.isEmpty()) {
      return 0L;
    }
    String in = projectIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(1)
            FROM bluedock_task_templates t
            INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
            WHERE t.project_id IN (
            """);
    sql.append(in).append(")");
    List<Object> args = new java.util.ArrayList<>(projectIds);
    appendKeyword(sql, args, keyword);
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  public List<TaskTemplate> search(
      List<Long> projectIds, String keyword, int offset, int limit) {
    if (projectIds == null || projectIds.isEmpty()) {
      return List.of();
    }
    String in = projectIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT t.id, t.project_id, t.name, t.title, t.content, t.sort, t.is_default, t.user_id,
                   t.use_count, t.last_used_at, t.created_at, t.updated_at,
                   p.name AS project_name, COALESCE(u.nickname, '') AS user_name
            FROM bluedock_task_templates t
            INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
            LEFT JOIN bluedock_users u ON u.id = t.user_id
            WHERE t.project_id IN (
            """);
    sql.append(in).append(")");
    List<Object> args = new java.util.ArrayList<>(projectIds);
    appendKeyword(sql, args, keyword);
    sql.append(
        """
         ORDER BY t.use_count DESC, t.last_used_at DESC, t.created_at DESC
         LIMIT ? OFFSET ?
        """);
    args.add(limit);
    args.add(offset);
    return jdbc.query(
        sql.toString(),
        (rs, i) -> {
          TaskTemplate t = mapRow(rs, i);
          t.setProjectName(rs.getString("project_name"));
          t.setUserName(rs.getString("user_name"));
          return t;
        },
        args.toArray());
  }

  private static void appendKeyword(StringBuilder sql, List<Object> args, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return;
    }
    String like = "%" + escapeLike(keyword.trim()) + "%";
    sql.append(
        """
         AND (t.name LIKE ? ESCAPE '!'
              OR t.title LIKE ? ESCAPE '!'
              OR t.content LIKE ? ESCAPE '!')
        """);
    args.add(like);
    args.add(like);
    args.add(like);
  }

  private static String escapeLike(String s) {
    return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private static Object[] buildArgs(long preferProjectId, List<Long> projectIds) {
    Object[] args = new Object[projectIds.size() + 1];
    int i = 0;
    for (Long id : projectIds) {
      args[i++] = id;
    }
    args[i] = preferProjectId;
    return args;
  }

  private static TaskTemplate mapRow(ResultSet rs, int i) throws SQLException {
    TaskTemplate t = new TaskTemplate();
    t.setId(rs.getLong("id"));
    t.setProjectId(rs.getLong("project_id"));
    t.setName(rs.getString("name"));
    t.setTitle(rs.getString("title"));
    t.setContent(rs.getString("content"));
    t.setSort(rs.getInt("sort"));
    t.setIsDefault(rs.getInt("is_default"));
    t.setUserId(rs.getLong("user_id"));
    t.setUseCount(rs.getInt("use_count"));
    Timestamp lu = rs.getTimestamp("last_used_at");
    if (lu != null) {
      t.setLastUsedAt(lu.toLocalDateTime());
    }
    Timestamp c = rs.getTimestamp("created_at");
    Timestamp u = rs.getTimestamp("updated_at");
    if (c != null) {
      t.setCreatedAt(c.toLocalDateTime());
    }
    if (u != null) {
      t.setUpdatedAt(u.toLocalDateTime());
    }
    return t;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static Timestamp toTs(LocalDateTime v) {
    return v == null ? null : Timestamp.valueOf(v);
  }
}
