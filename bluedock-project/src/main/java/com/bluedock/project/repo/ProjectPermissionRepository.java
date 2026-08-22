package com.bluedock.project.repo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectPermissionRepository {
  private final JdbcTemplate jdbc;

  public ProjectPermissionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> findJson(long projectId) {
    var list =
        jdbc.query(
            "SELECT permissions FROM bluedock_project_permissions WHERE project_id = ?",
            (rs, i) -> rs.getString(1),
            projectId);
    return list.stream().findFirst();
  }

  public void upsert(long id, long projectId, String json) {
    LocalDateTime now = LocalDateTime.now();
    int updated =
        jdbc.update(
            """
            UPDATE bluedock_project_permissions
            SET permissions = ?, updated_at = ?
            WHERE project_id = ?
            """,
            json,
            Timestamp.valueOf(now),
            projectId);
    if (updated == 0) {
      jdbc.update(
          """
          INSERT INTO bluedock_project_permissions (id, project_id, permissions, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?)
          """,
          id,
          projectId,
          json,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now));
    }
  }
}
