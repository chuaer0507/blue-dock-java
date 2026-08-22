package com.bluedock.project.repo;

import com.bluedock.project.domain.ProjectColumn;
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
public class ProjectColumnRepository {
  private static final RowMapper<ProjectColumn> MAPPER = ProjectColumnRepository::mapRow;

  private final JdbcTemplate jdbc;

  public ProjectColumnRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(ProjectColumn c) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_project_columns
          (id, project_id, name, color, sort, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        c.getId(),
        c.getProjectId(),
        c.getName(),
        c.getColor() == null ? "" : c.getColor(),
        c.getSort(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void update(ProjectColumn c) {
    jdbc.update(
        """
        UPDATE bluedock_project_columns
        SET name = ?, color = ?, sort = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        c.getName(),
        c.getColor() == null ? "" : c.getColor(),
        c.getSort(),
        Timestamp.valueOf(LocalDateTime.now()),
        c.getId());
  }

  public void updateSort(long id, long projectId, int sort) {
    jdbc.update(
        """
        UPDATE bluedock_project_columns
        SET sort = ?, updated_at = ?
        WHERE id = ? AND project_id = ? AND deleted_at IS NULL
        """,
        sort,
        Timestamp.valueOf(LocalDateTime.now()),
        id,
        projectId);
  }

  public List<ProjectColumn> listByProject(long projectId) {
    return jdbc.query(
        """
        SELECT id, project_id, name, color, sort, deleted_at
        FROM bluedock_project_columns
        WHERE project_id = ? AND deleted_at IS NULL
        ORDER BY sort ASC, id ASC
        """,
        MAPPER,
        projectId);
  }

  public Optional<ProjectColumn> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, name, color, sort, deleted_at
            FROM bluedock_project_columns
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public int countActiveByProject(long projectId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_project_columns
            WHERE project_id = ? AND deleted_at IS NULL
            """,
            Integer.class,
            projectId);
    return n == null ? 0 : n;
  }

  public void softDelete(long id) {
    jdbc.update(
        """
        UPDATE bluedock_project_columns
        SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  private static ProjectColumn mapRow(ResultSet rs, int rowNum) throws SQLException {
    ProjectColumn c = new ProjectColumn();
    c.setId(rs.getLong("id"));
    c.setProjectId(rs.getLong("project_id"));
    c.setName(rs.getString("name"));
    c.setColor(rs.getString("color"));
    c.setSort(rs.getInt("sort"));
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      c.setDeletedAt(deleted.toLocalDateTime());
    }
    return c;
  }
}
