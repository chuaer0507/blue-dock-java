package com.bluedock.project.repo;

import com.bluedock.project.domain.ProjectTag;
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
public class ProjectTagRepository {
  private static final RowMapper<ProjectTag> MAPPER = ProjectTagRepository::mapRow;

  private final JdbcTemplate jdbc;

  public ProjectTagRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(ProjectTag t) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_project_tags
          (id, project_id, name, color, sort, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        t.getId(),
        t.getProjectId(),
        t.getName(),
        t.getColor() == null ? "" : t.getColor(),
        t.getSort(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void update(ProjectTag t) {
    jdbc.update(
        """
        UPDATE bluedock_project_tags
        SET name = ?, color = ?, sort = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        t.getName(),
        t.getColor() == null ? "" : t.getColor(),
        t.getSort(),
        Timestamp.valueOf(LocalDateTime.now()),
        t.getId());
  }

  public void updateSort(long id, long projectId, int sort) {
    jdbc.update(
        """
        UPDATE bluedock_project_tags
        SET sort = ?, updated_at = ?
        WHERE id = ? AND project_id = ? AND deleted_at IS NULL
        """,
        sort,
        Timestamp.valueOf(LocalDateTime.now()),
        id,
        projectId);
  }

  public void softDelete(long id) {
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    jdbc.update(
        "UPDATE bluedock_project_tags SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
        now,
        now,
        id);
    jdbc.update("DELETE FROM bluedock_task_tags WHERE tag_id = ?", id);
  }

  public Optional<ProjectTag> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, name, color, sort, deleted_at
            FROM bluedock_project_tags
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<ProjectTag> listByProject(long projectId) {
    return jdbc.query(
        """
        SELECT id, project_id, name, color, sort, deleted_at
        FROM bluedock_project_tags
        WHERE project_id = ? AND deleted_at IS NULL
        ORDER BY sort ASC, id ASC
        """,
        MAPPER,
        projectId);
  }

  public Optional<ProjectTag> findByProjectAndName(long projectId, String name) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, name, color, sort, deleted_at
            FROM bluedock_project_tags
            WHERE project_id = ? AND name = ? AND deleted_at IS NULL
            """,
            MAPPER,
            projectId,
            name);
    return list.stream().findFirst();
  }

  public int countByProject(long projectId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_project_tags WHERE project_id = ? AND deleted_at IS NULL",
            Integer.class,
            projectId);
    return n == null ? 0 : n;
  }

  public List<ProjectTag> listByIds(long projectId, java.util.Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    String in = ids.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(projectId);
    args.addAll(ids);
    return jdbc.query(
        """
        SELECT id, project_id, name, color, sort, deleted_at
        FROM bluedock_project_tags
        WHERE project_id = ? AND deleted_at IS NULL AND id IN (
        """
            + in
            + ") ORDER BY sort ASC, id ASC",
        MAPPER,
        args.toArray());
  }

  private static ProjectTag mapRow(ResultSet rs, int rowNum) throws SQLException {
    ProjectTag t = new ProjectTag();
    t.setId(rs.getLong("id"));
    t.setProjectId(rs.getLong("project_id"));
    t.setName(rs.getString("name"));
    t.setColor(rs.getString("color"));
    t.setSort(rs.getInt("sort"));
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      t.setDeletedAt(deleted.toLocalDateTime());
    }
    return t;
  }
}
