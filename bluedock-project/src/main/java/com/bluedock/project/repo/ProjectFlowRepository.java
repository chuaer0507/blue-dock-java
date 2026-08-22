package com.bluedock.project.repo;

import com.bluedock.project.domain.ProjectFlow;
import com.bluedock.project.domain.ProjectFlowItem;
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
public class ProjectFlowRepository {
  private static final RowMapper<ProjectFlow> FLOW_MAPPER = ProjectFlowRepository::mapFlow;
  private static final RowMapper<ProjectFlowItem> ITEM_MAPPER = ProjectFlowRepository::mapItem;

  private final JdbcTemplate jdbc;

  public ProjectFlowRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insertFlow(ProjectFlow f) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_project_flows (id, project_id, name, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        f.getId(),
        f.getProjectId(),
        f.getName(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void updateFlow(ProjectFlow f) {
    jdbc.update(
        """
        UPDATE bluedock_project_flows SET name = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        f.getName(),
        Timestamp.valueOf(LocalDateTime.now()),
        f.getId());
  }

  public void softDeleteFlow(long id) {
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    jdbc.update(
        "UPDATE bluedock_project_flows SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
        now,
        now,
        id);
    jdbc.update(
        "UPDATE bluedock_project_flow_items SET deleted_at = ?, updated_at = ? WHERE flow_id = ? AND deleted_at IS NULL",
        now,
        now,
        id);
  }

  public Optional<ProjectFlow> findActiveFlow(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, project_id, name, deleted_at FROM bluedock_project_flows
            WHERE id = ? AND deleted_at IS NULL
            """,
            FLOW_MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<ProjectFlow> listFlowsByProject(long projectId) {
    return jdbc.query(
        """
        SELECT id, project_id, name, deleted_at FROM bluedock_project_flows
        WHERE project_id = ? AND deleted_at IS NULL
        ORDER BY id ASC
        """,
        FLOW_MAPPER,
        projectId);
  }

  public void insertItem(ProjectFlowItem it) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_project_flow_items
          (id, flow_id, project_id, name, status, color, sort, turns, user_ids, user_type, column_id,
           created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        it.getId(),
        it.getFlowId(),
        it.getProjectId(),
        it.getName(),
        it.getStatus(),
        nullToEmpty(it.getColor()),
        it.getSort(),
        nullToEmpty(it.getTurns()),
        nullToEmpty(it.getUserIds()),
        nullToEmpty(it.getUsertype()),
        it.getColumnId(),
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void updateItem(ProjectFlowItem it) {
    jdbc.update(
        """
        UPDATE bluedock_project_flow_items
        SET name = ?, status = ?, color = ?, sort = ?, turns = ?, user_ids = ?, user_type = ?,
            column_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        it.getName(),
        it.getStatus(),
        nullToEmpty(it.getColor()),
        it.getSort(),
        nullToEmpty(it.getTurns()),
        nullToEmpty(it.getUserIds()),
        nullToEmpty(it.getUsertype()),
        it.getColumnId(),
        Timestamp.valueOf(LocalDateTime.now()),
        it.getId());
  }

  public void softDeleteItemsNotIn(long flowId, java.util.Collection<Long> keep) {
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    if (keep == null || keep.isEmpty()) {
      jdbc.update(
          """
          UPDATE bluedock_project_flow_items
          SET deleted_at = ?, updated_at = ?
          WHERE flow_id = ? AND deleted_at IS NULL
          """,
          now,
          now,
          flowId);
      return;
    }
    String in = keep.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(now);
    args.add(now);
    args.add(flowId);
    args.addAll(keep);
    jdbc.update(
        """
        UPDATE bluedock_project_flow_items
        SET deleted_at = ?, updated_at = ?
        WHERE flow_id = ? AND deleted_at IS NULL AND id NOT IN (
        """
            + in
            + ")",
        args.toArray());
  }

  public List<ProjectFlowItem> listItemsByFlow(long flowId) {
    return jdbc.query(
        """
        SELECT id, flow_id, project_id, name, status, color, sort, turns, user_ids, user_type, column_id,
               deleted_at
        FROM bluedock_project_flow_items
        WHERE flow_id = ? AND deleted_at IS NULL
        ORDER BY sort ASC, id ASC
        """,
        ITEM_MAPPER,
        flowId);
  }

  public Optional<ProjectFlowItem> findActiveItem(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, flow_id, project_id, name, status, color, sort, turns, user_ids, user_type, column_id,
                   deleted_at
            FROM bluedock_project_flow_items
            WHERE id = ? AND deleted_at IS NULL
            """,
            ITEM_MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<ProjectFlowItem> listItemsByProject(long projectId) {
    return jdbc.query(
        """
        SELECT id, flow_id, project_id, name, status, color, sort, turns, user_ids, user_type, column_id,
               deleted_at
        FROM bluedock_project_flow_items
        WHERE project_id = ? AND deleted_at IS NULL
        ORDER BY flow_id ASC, sort ASC, id ASC
        """,
        ITEM_MAPPER,
        projectId);
  }

  /** 列绑定的工作流节点（同列多绑定时取 sort/id 最小）。 */
  public Optional<ProjectFlowItem> findActiveItemByColumn(long projectId, long columnId) {
    if (columnId <= 0) {
      return Optional.empty();
    }
    var list =
        jdbc.query(
            """
            SELECT id, flow_id, project_id, name, status, color, sort, turns, user_ids, user_type, column_id,
                   deleted_at
            FROM bluedock_project_flow_items
            WHERE project_id = ? AND column_id = ? AND deleted_at IS NULL
            ORDER BY sort ASC, id ASC
            LIMIT 1
            """,
            ITEM_MAPPER,
            projectId,
            columnId);
    return list.stream().findFirst();
  }

  private static ProjectFlow mapFlow(ResultSet rs, int rowNum) throws SQLException {
    ProjectFlow f = new ProjectFlow();
    f.setId(rs.getLong("id"));
    f.setProjectId(rs.getLong("project_id"));
    f.setName(rs.getString("name"));
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      f.setDeletedAt(deleted.toLocalDateTime());
    }
    return f;
  }

  private static ProjectFlowItem mapItem(ResultSet rs, int rowNum) throws SQLException {
    ProjectFlowItem it = new ProjectFlowItem();
    it.setId(rs.getLong("id"));
    it.setFlowId(rs.getLong("flow_id"));
    it.setProjectId(rs.getLong("project_id"));
    it.setName(rs.getString("name"));
    it.setStatus(rs.getString("status"));
    it.setColor(rs.getString("color"));
    it.setSort(rs.getInt("sort"));
    it.setTurns(rs.getString("turns"));
    it.setUserIds(rs.getString("user_ids"));
    it.setUsertype(rs.getString("user_type"));
    it.setColumnId(rs.getLong("column_id"));
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      it.setDeletedAt(deleted.toLocalDateTime());
    }
    return it;
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
