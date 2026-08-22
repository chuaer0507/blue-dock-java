package com.bluedock.project.repo;

import com.bluedock.project.domain.ProjectInvite;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectInviteRepository {
  private static final RowMapper<ProjectInvite> MAPPER = ProjectInviteRepository::mapRow;

  private final JdbcTemplate jdbc;

  public ProjectInviteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(ProjectInvite invite) {
    jdbc.update(
        """
        INSERT INTO bluedock_project_invites
          (id, project_id, code, user_id, expired_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        invite.getId(),
        invite.getProjectId(),
        invite.getCode(),
        invite.getUserId(),
        invite.getExpiredAt() == null ? null : Timestamp.valueOf(invite.getExpiredAt()),
        Timestamp.valueOf(invite.getCreatedAt()),
        Timestamp.valueOf(invite.getCreatedAt()));
  }

  public Optional<ProjectInvite> findActiveByProject(long projectId) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, code, user_id, expired_at, created_at
            FROM bluedock_project_invites
            WHERE project_id = ?
              AND (expired_at IS NULL OR expired_at > ?)
            ORDER BY id DESC
            LIMIT 1
            """,
            MAPPER,
            projectId,
            Timestamp.valueOf(LocalDateTime.now()));
    return list.stream().findFirst();
  }

  public Optional<ProjectInvite> findByCode(String code) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, code, user_id, expired_at, created_at
            FROM bluedock_project_invites
            WHERE code = ?
            """,
            MAPPER,
            code);
    return list.stream().findFirst();
  }

  private static ProjectInvite mapRow(ResultSet rs, int rowNum) throws SQLException {
    ProjectInvite i = new ProjectInvite();
    i.setId(rs.getLong("id"));
    i.setProjectId(rs.getLong("project_id"));
    i.setCode(rs.getString("code"));
    i.setUserId(rs.getLong("user_id"));
    Timestamp expired = rs.getTimestamp("expired_at");
    if (expired != null) {
      i.setExpiredAt(expired.toLocalDateTime());
    }
    Timestamp created = rs.getTimestamp("created_at");
    if (created != null) {
      i.setCreatedAt(created.toLocalDateTime());
    }
    return i;
  }
}
