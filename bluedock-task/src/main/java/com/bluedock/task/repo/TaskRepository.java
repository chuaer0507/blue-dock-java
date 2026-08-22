package com.bluedock.task.repo;

import com.bluedock.task.domain.TaskItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {
  private static final RowMapper<TaskItem> MAPPER = TaskRepository::mapRow;

  private final JdbcTemplate jdbc;

  public TaskRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(TaskItem t) {
    jdbc.update(
        """
            INSERT INTO bluedock_tasks
              (id, parent_id, project_id, column_id, dialog_id, name, color, description,
               start_at, end_at, complete_at, visibility, priority_level, priority_name, priority_color,
               flow_item_id, flow_item_name, sort, `loop`, loop_at, user_id,
               created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        t.getId(),
        t.getParentId(),
        t.getProjectId(),
        t.getColumnId(),
        t.getDialogId(),
        t.getName(),
        nullToEmpty(t.getColor()),
        nullToEmpty(t.getDescription()),
        toTs(t.getStartAt()),
        toTs(t.getEndAt()),
        toTs(t.getCompleteAt()),
        t.getVisibility(),
        t.getPriorityLevel(),
        nullToEmpty(t.getPriorityName()),
        nullToEmpty(t.getPriorityColor()),
        t.getFlowItemId(),
        nullToEmpty(t.getFlowItemName()),
        t.getSort(),
        t.getLoop(),
        toTs(t.getLoopAt()),
        t.getUserId(),
        toTs(t.getCreatedAt()),
        toTs(t.getUpdatedAt()));
  }

  public void update(TaskItem t) {
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET parent_id = ?, project_id = ?, name = ?, color = ?, description = ?, column_id = ?,
                start_at = ?, end_at = ?, complete_at = ?, visibility = ?,
                priority_level = ?, priority_name = ?, priority_color = ?, flow_item_id = ?, flow_item_name = ?,
                sort = ?, `loop` = ?, loop_at = ?, updated_at = ?
            WHERE id = ? AND deleted_at IS NULL
            """,
        t.getParentId(),
        t.getProjectId(),
        t.getName(),
        nullToEmpty(t.getColor()),
        nullToEmpty(t.getDescription()),
        t.getColumnId(),
        toTs(t.getStartAt()),
        toTs(t.getEndAt()),
        toTs(t.getCompleteAt()),
        t.getVisibility(),
        t.getPriorityLevel(),
        nullToEmpty(t.getPriorityName()),
        nullToEmpty(t.getPriorityColor()),
        t.getFlowItemId(),
        nullToEmpty(t.getFlowItemName()),
        t.getSort(),
        t.getLoop(),
        toTs(t.getLoopAt()),
        Timestamp.valueOf(LocalDateTime.now()),
        t.getId());
  }

  /** 子任务随主任务换项目/列。 */
  public void moveChildrenLocation(long parentId, long projectId, long columnId) {
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET project_id = ?, column_id = ?, updated_at = ?
            WHERE parent_id = ? AND deleted_at IS NULL
            """,
        projectId,
        columnId,
        Timestamp.valueOf(LocalDateTime.now()),
        parentId);
  }

  /**
   * 看板拖拽：更新未完成任务的列与排序。返回受影响行数（0=不存在/已完成/非本项目）。
   */
  public int updateColumnAndSortIfIncomplete(
      long taskId, long projectId, long columnId, int sort) {
    return jdbc.update(
        """
            UPDATE bluedock_tasks
            SET column_id = ?, sort = ?, updated_at = ?
            WHERE id = ? AND project_id = ? AND deleted_at IS NULL AND complete_at IS NULL
            """,
        columnId,
        sort,
        Timestamp.valueOf(LocalDateTime.now()),
        taskId,
        projectId);
  }

  /**
   * 拖列联动工作流：写入绑定节点；{@code markComplete=true} 时补写 {@code complete_at}（不回清空）。
   */
  public int applyBoundFlowFromColumn(
      long taskId,
      long projectId,
      long flowItemId,
      String flowItemName,
      boolean markComplete) {
    LocalDateTime now = LocalDateTime.now();
    if (markComplete) {
      return jdbc.update(
          """
              UPDATE bluedock_tasks
              SET flow_item_id = ?, flow_item_name = ?,
                  complete_at = IFNULL(complete_at, ?), updated_at = ?
              WHERE id = ? AND project_id = ? AND deleted_at IS NULL
              """,
          flowItemId,
          flowItemName == null ? "" : flowItemName,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now),
          taskId,
          projectId);
    }
    return jdbc.update(
        """
            UPDATE bluedock_tasks
            SET flow_item_id = ?, flow_item_name = ?, updated_at = ?
            WHERE id = ? AND project_id = ? AND deleted_at IS NULL AND complete_at IS NULL
            """,
        flowItemId,
        flowItemName == null ? "" : flowItemName,
        Timestamp.valueOf(now),
        taskId,
        projectId);
  }

  /** 子任务随主任务换列后同步绑定工作流节点。 */
  public int applyBoundFlowFromColumnForChildren(
      long parentId,
      long projectId,
      long flowItemId,
      String flowItemName,
      boolean markComplete) {
    LocalDateTime now = LocalDateTime.now();
    if (markComplete) {
      return jdbc.update(
          """
              UPDATE bluedock_tasks
              SET flow_item_id = ?, flow_item_name = ?,
                  complete_at = IFNULL(complete_at, ?), updated_at = ?
              WHERE parent_id = ? AND project_id = ? AND deleted_at IS NULL
              """,
          flowItemId,
          flowItemName == null ? "" : flowItemName,
          Timestamp.valueOf(now),
          Timestamp.valueOf(now),
          parentId,
          projectId);
    }
    return jdbc.update(
        """
            UPDATE bluedock_tasks
            SET flow_item_id = ?, flow_item_name = ?, updated_at = ?
            WHERE parent_id = ? AND project_id = ? AND deleted_at IS NULL AND complete_at IS NULL
            """,
        flowItemId,
        flowItemName == null ? "" : flowItemName,
        Timestamp.valueOf(now),
        parentId,
        projectId);
  }

  public void updateAssigneesProject(long taskId, long projectId) {
    jdbc.update(
        """
            UPDATE bluedock_task_users SET project_id = ?, updated_at = ?
            WHERE task_id = ?
            """,
        projectId,
        Timestamp.valueOf(LocalDateTime.now()),
        taskId);
  }

  public void updateAssigneeParentTaskId(long taskId, long parentTaskId) {
    jdbc.update(
        """
            UPDATE bluedock_task_users SET parent_task_id = ?, updated_at = ?
            WHERE task_id = ?
            """,
        parentTaskId,
        Timestamp.valueOf(LocalDateTime.now()),
        taskId);
  }

  public void updateFilesProject(long taskId, long projectId) {
    jdbc.update(
        """
            UPDATE bluedock_task_files SET project_id = ?, updated_at = ?
            WHERE task_id = ? AND deleted_at IS NULL
            """,
        projectId,
        Timestamp.valueOf(LocalDateTime.now()),
        taskId);
  }

  public Optional<TaskItem> findActive(long id) {
    var list = jdbc.query(
        """
            SELECT id, parent_id, project_id, column_id, dialog_id, name, color, description,
                   start_at, end_at, complete_at, visibility, priority_level, priority_name, priority_color, flow_item_id, flow_item_name, sort, `loop`, loop_at, user_id,
                   archived_at, deleted_at, created_at, updated_at
            FROM bluedock_tasks
            WHERE id = ? AND deleted_at IS NULL
            """,
        MAPPER,
        id);
    return list.stream().findFirst();
  }

  public List<TaskItem> listByProject(long projectId, Long columnId, boolean includeArchived) {
    return listByProject(projectId, columnId, includeArchived, null);
  }

  /**
   * 主任务列表；{@code viewerUserId} 非空时按可见性过滤（1 全员 · 2 任务人员 · 3 任务人员+指定成员）。
   */
  public List<TaskItem> listByProject(
      long projectId, Long columnId, boolean includeArchived, Long viewerUserId) {
    String archived = includeArchived ? "" : " AND archived_at IS NULL";
    String visibilitySql = "";
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(projectId);
    if (columnId != null) {
      args.add(columnId);
    }
    if (viewerUserId != null) {
      visibilitySql = """
           AND (
            visibility = 1
            OR (
              visibility = 2 AND EXISTS (
                SELECT 1 FROM bluedock_task_users u
                WHERE u.task_id = bluedock_tasks.id AND u.user_id = ?
              )
            )
            OR (
              visibility = 3 AND (
                EXISTS (
                  SELECT 1 FROM bluedock_task_users u
                  WHERE u.task_id = bluedock_tasks.id AND u.user_id = ?
                )
                OR EXISTS (
                  SELECT 1 FROM bluedock_task_visibility_users v
                  WHERE v.task_id = bluedock_tasks.id AND v.user_id = ?
                )
              )
            )
          )
          """;
      args.add(viewerUserId);
      args.add(viewerUserId);
      args.add(viewerUserId);
    }
    if (columnId != null) {
      return jdbc.query(
          """
              SELECT id, parent_id, project_id, column_id, dialog_id, name, color, description,
                     start_at, end_at, complete_at, visibility, priority_level, priority_name, priority_color, flow_item_id, flow_item_name, sort, `loop`, loop_at, user_id,
                     archived_at, deleted_at, created_at, updated_at
              FROM bluedock_tasks
              WHERE project_id = ? AND column_id = ? AND deleted_at IS NULL AND parent_id = 0
              """
              + archived
              + visibilitySql
              + " ORDER BY sort ASC, id ASC",
          MAPPER,
          args.toArray());
    }
    return jdbc.query(
        """
            SELECT id, parent_id, project_id, column_id, dialog_id, name, color, description,
                   start_at, end_at, complete_at, visibility, priority_level, priority_name, priority_color, flow_item_id, flow_item_name, sort, `loop`, loop_at, user_id,
                   archived_at, deleted_at, created_at, updated_at
            FROM bluedock_tasks
            WHERE project_id = ? AND deleted_at IS NULL AND parent_id = 0
            """
            + archived
            + visibilitySql
            + " ORDER BY sort ASC, id ASC",
        MAPPER,
        args.toArray());
  }

  public void updateChildrenVisibility(long parentId, int visibility) {
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET visibility = ?, updated_at = ?
            WHERE parent_id = ? AND deleted_at IS NULL
            """,
        visibility,
        Timestamp.valueOf(LocalDateTime.now()),
        parentId);
  }

  public boolean isAssignee(long taskId, long userId) {
    Integer n = jdbc.queryForObject(
        "SELECT COUNT(1) FROM bluedock_task_users WHERE task_id = ? AND user_id = ?",
        Integer.class,
        taskId,
        userId);
    return n != null && n > 0;
  }

  public int nextSort(long projectId, long columnId) {
    Integer n = jdbc.queryForObject(
        """
            SELECT COALESCE(MAX(sort), -1) + 1 FROM bluedock_tasks
            WHERE project_id = ? AND column_id = ? AND deleted_at IS NULL
            """,
        Integer.class,
        projectId,
        columnId);
    return n == null ? 0 : n;
  }

  public void insertAssignee(long id, long taskId, long parentTaskId, long projectId, long userId, int owner) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
            INSERT INTO bluedock_task_users
              (id, task_id, parent_task_id, project_id, user_id, owner, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              parent_task_id = VALUES(parent_task_id),
              project_id = VALUES(project_id),
              owner = VALUES(owner),
              updated_at = VALUES(updated_at)
            """,
        id,
        taskId,
        parentTaskId,
        projectId,
        userId,
        owner,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  /** 删除指定 owner 标记且不在 keep 中的成员；{@code keep} 空则删光该标记。 */
  public void deleteAssigneesNotIn(long taskId, int owner, java.util.Collection<Long> keep) {
    if (keep == null || keep.isEmpty()) {
      jdbc.update(
          "DELETE FROM bluedock_task_users WHERE task_id = ? AND owner = ?", taskId, owner);
      return;
    }
    String in = keep.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(taskId);
    args.add(owner);
    args.addAll(keep);
    jdbc.update(
        "DELETE FROM bluedock_task_users WHERE task_id = ? AND owner = ? AND user_id NOT IN ("
            + in
            + ")",
        args.toArray());
  }

  public List<Long> listAssigneeUserIds(long taskId) {
    return jdbc.query(
        "SELECT user_id FROM bluedock_task_users WHERE task_id = ?",
        (rs, i) -> rs.getLong(1),
        taskId);
  }

  /** owner: 1 负责人 · 0 协助。 */
  public List<long[]> listAssignees(long taskId) {
    return jdbc.query(
        "SELECT user_id, owner FROM bluedock_task_users WHERE task_id = ? ORDER BY owner DESC, id ASC",
        (rs, i) -> new long[] { rs.getLong(1), rs.getInt(2) },
        taskId);
  }

  /** 用户作为任务负责人（owner=1）的任务 id。 */
  public List<Long> listTaskIdsOwnedBy(long userId) {
    return jdbc.query(
        """
            SELECT DISTINCT tu.task_id
            FROM bluedock_task_users tu
            INNER JOIN bluedock_tasks t ON t.id = tu.task_id
            WHERE tu.user_id = ? AND tu.owner = 1 AND t.deleted_at IS NULL
            """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  /** 读取任务一行成员信息（用于交接复制 parent/project）。 */
  public Optional<long[]> findAssigneeRow(long taskId, long userId) {
    var list = jdbc.query(
        """
            SELECT parent_task_id, project_id, owner
            FROM bluedock_task_users
            WHERE task_id = ? AND user_id = ?
            LIMIT 1
            """,
        (rs, i) -> new long[] { rs.getLong(1), rs.getLong(2), rs.getInt(3) },
        taskId,
        userId);
    return list.stream().findFirst();
  }

  public void deleteAssignee(long taskId, long userId) {
    jdbc.update("DELETE FROM bluedock_task_users WHERE task_id = ? AND user_id = ?", taskId, userId);
  }

  public void deleteAllAssigneesForUser(long userId) {
    jdbc.update("DELETE FROM bluedock_task_users WHERE user_id = ?", userId);
  }

  public void updateDialogId(long taskId, long dialogId) {
    jdbc.update(
        """
            UPDATE bluedock_tasks SET dialog_id = ?, updated_at = ?
            WHERE id = ? AND deleted_at IS NULL
            """,
        dialogId,
        Timestamp.valueOf(LocalDateTime.now()),
        taskId);
  }

  /** 挂在该任务群上的未删除任务 ID（含子任务）。 */
  public List<Long> listIdsByDialogId(long dialogId) {
    if (dialogId <= 0) {
      return List.of();
    }
    return jdbc.query(
        """
            SELECT id FROM bluedock_tasks
            WHERE dialog_id = ? AND deleted_at IS NULL
            ORDER BY id ASC
            """,
        (rs, i) -> rs.getLong(1),
        dialogId);
  }

  public List<TaskItem> listByParent(long parentId) {
    return jdbc.query(
        """
            SELECT id, parent_id, project_id, column_id, dialog_id, name, color, description,
                   start_at, end_at, complete_at, visibility, priority_level, priority_name, priority_color, flow_item_id, flow_item_name, sort, `loop`, loop_at, user_id,
                   archived_at, deleted_at, created_at, updated_at
            FROM bluedock_tasks
            WHERE parent_id = ? AND deleted_at IS NULL
            ORDER BY sort ASC, id ASC
            """,
        MAPPER,
        parentId);
  }

  public int countChildren(long parentId) {
    Integer n = jdbc.queryForObject(
        """
            SELECT COUNT(1) FROM bluedock_tasks
            WHERE parent_id = ? AND deleted_at IS NULL
            """,
        Integer.class,
        parentId);
    return n == null ? 0 : n;
  }

  public void archive(long taskId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET archived_at = ?, archived_user_id = ?, updated_at = ?
            WHERE id = ? AND deleted_at IS NULL
            """,
        Timestamp.valueOf(now),
        userId,
        Timestamp.valueOf(now),
        taskId);
  }

  /** 项目归档：未归档任务一并归档并标记 archived_follow=1。 */
  public void archiveByProject(long projectId, long userId, LocalDateTime archivedAt) {
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET archived_at = ?, archived_user_id = ?, archived_follow = 1, updated_at = ?
            WHERE project_id = ? AND deleted_at IS NULL AND archived_at IS NULL
            """,
        Timestamp.valueOf(archivedAt),
        userId,
        Timestamp.valueOf(archivedAt),
        projectId);
  }

  /** 项目取消归档：仅恢复 archived_follow=1 的任务。 */
  public void unarchiveFollowedByProject(long projectId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET archived_at = NULL, archived_user_id = ?, archived_follow = 0, updated_at = ?
            WHERE project_id = ? AND deleted_at IS NULL AND archived_follow = 1
            """,
        userId,
        Timestamp.valueOf(now),
        projectId);
  }

  public void softDelete(long taskId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET deleted_at = ?, deleted_user_id = ?, updated_at = ?
            WHERE id = ? AND deleted_at IS NULL
            """,
        Timestamp.valueOf(now),
        userId,
        Timestamp.valueOf(now),
        taskId);
  }

  public void softDeleteChildren(long parentId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET deleted_at = ?, deleted_user_id = ?, updated_at = ?
            WHERE parent_id = ? AND deleted_at IS NULL
            """,
        Timestamp.valueOf(now),
        userId,
        Timestamp.valueOf(now),
        parentId);
  }

  /** 软删指定列下全部任务（主/子）；返回受影响行数。 */
  public int softDeleteByColumn(long projectId, long columnId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    return jdbc.update(
        """
            UPDATE bluedock_tasks
            SET deleted_at = ?, deleted_user_id = ?, updated_at = ?
            WHERE project_id = ? AND column_id = ? AND deleted_at IS NULL
            """,
        Timestamp.valueOf(now),
        userId,
        Timestamp.valueOf(now),
        projectId,
        columnId);
  }

  /**
   * 团队仪表盘任务：主任务、全员可见、未归档删除；负责人为成员或无负责人。
   * projectIds / memberIds 为空时返回空列表。
   */
  public List<TaskItem> listTeamTasks(List<Long> projectIds, List<Long> memberIds) {
    if (projectIds == null
        || projectIds.isEmpty()
        || memberIds == null
        || memberIds.isEmpty()) {
      return List.of();
    }
    String pIn = projectIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    String mIn = memberIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    Object[] args = new Object[projectIds.size() + memberIds.size()];
    int i = 0;
    for (Long id : projectIds) {
      args[i++] = id;
    }
    for (Long id : memberIds) {
      args[i++] = id;
    }
    return jdbc.query(
        """
            SELECT t.id, t.parent_id, t.project_id, t.column_id, t.dialog_id, t.name, t.color, t.description,
                   t.start_at, t.end_at, t.complete_at, t.visibility, t.priority_level, t.priority_name, t.priority_color, t.flow_item_id, t.flow_item_name, t.sort, t.`loop`, t.loop_at, t.user_id,
                   t.archived_at, t.deleted_at, t.created_at, t.updated_at,
                   (SELECT tu0.user_id FROM bluedock_task_users tu0
                     WHERE tu0.task_id = t.id AND tu0.owner = 1 LIMIT 1) AS owner_user_id
            FROM bluedock_tasks t
            WHERE t.deleted_at IS NULL AND t.archived_at IS NULL AND t.parent_id = 0 AND t.visibility = 1
              AND t.project_id IN (
            """
            + pIn
            + """
                  )
                  AND (
                    EXISTS (
                      SELECT 1 FROM bluedock_task_users tu
                      WHERE tu.task_id = t.id AND tu.owner = 1 AND tu.user_id IN (
                """
            + mIn
            + """
                      )
                    )
                    OR NOT EXISTS (
                      SELECT 1 FROM bluedock_task_users tu2 WHERE tu2.task_id = t.id AND tu2.owner = 1
                    )
                  )
                ORDER BY t.end_at IS NULL, t.end_at ASC, t.id DESC
                """,
        TaskRepository::mapTeamRow,
        args);
  }

  /** 当前用户负责或协助、且时间区间与 [start,end] 相交的主任务（日历）。 */
  public List<TaskItem> listCalendarForUser(long userId, LocalDateTime start, LocalDateTime end) {
    return jdbc.query(
        """
            SELECT t.id, t.parent_id, t.project_id, t.column_id, t.dialog_id, t.name, t.color, t.description,
                   t.start_at, t.end_at, t.complete_at, t.visibility, t.priority_level, t.priority_name, t.priority_color, t.flow_item_id, t.flow_item_name, t.sort, t.`loop`, t.loop_at, t.user_id,
                   t.archived_at, t.deleted_at, t.created_at, t.updated_at
            FROM bluedock_tasks t
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ?
            WHERE t.deleted_at IS NULL AND t.archived_at IS NULL AND t.parent_id = 0
              AND t.start_at IS NOT NULL AND t.end_at IS NOT NULL
              AND t.start_at <= ? AND t.end_at >= ?
            ORDER BY t.start_at ASC, t.id ASC
            """,
        MAPPER,
        userId,
        Timestamp.valueOf(end),
        Timestamp.valueOf(start));
  }

  /**
   * 计划时间冲突简表：指定负责人、未完成、可选时间窗相交；排除某一任务。
   * 返回行：id, name, project_id, project_name, start_at, end_at
   */
  public List<Map<String, Object>> listEasy(
      List<Long> ownerUserIds,
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd,
      Long excludeTaskId,
      int limit) {
    if (ownerUserIds == null || ownerUserIds.isEmpty()) {
      return List.of();
    }
    String in = ownerUserIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    StringBuilder sql = new StringBuilder(
        """
            SELECT DISTINCT t.id, t.name, t.project_id, p.name AS project_name, t.start_at, t.end_at
            FROM bluedock_tasks t
            INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.owner = 1 AND tu.user_id IN (
            """);
    sql.append(in).append(") ");
    sql.append(
        """
            WHERE t.deleted_at IS NULL AND t.archived_at IS NULL AND t.complete_at IS NULL
            """);
    List<Object> args = new ArrayList<>(ownerUserIds);
    if (rangeStart != null && rangeEnd != null) {
      sql.append(" AND t.start_at IS NOT NULL AND t.end_at IS NOT NULL");
      sql.append(" AND t.start_at <= ? AND t.end_at >= ?");
      args.add(Timestamp.valueOf(rangeEnd));
      args.add(Timestamp.valueOf(rangeStart));
    }
    if (excludeTaskId != null && excludeTaskId > 0) {
      sql.append(" AND t.id <> ?");
      args.add(excludeTaskId);
    }
    sql.append(" ORDER BY t.id DESC LIMIT ?");
    args.add(Math.min(Math.max(limit, 1), 200));
    return jdbc.query(
        sql.toString(),
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", rs.getLong("id"));
          m.put("name", rs.getString("name"));
          m.put("projectId", rs.getLong("project_id"));
          m.put("projectName", rs.getString("project_name") == null ? "" : rs.getString("project_name"));
          Timestamp s = rs.getTimestamp("start_at");
          Timestamp e = rs.getTimestamp("end_at");
          m.put("startAt", s == null ? null : s.toLocalDateTime().toString());
          m.put("endAt", e == null ? null : e.toLocalDateTime().toString());
          return m;
        },
        args.toArray());
  }

  public void archiveChildren(long parentId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
            UPDATE bluedock_tasks
            SET archived_at = ?, archived_user_id = ?, updated_at = ?
            WHERE parent_id = ? AND deleted_at IS NULL AND archived_at IS NULL
            """,
        Timestamp.valueOf(now),
        userId,
        Timestamp.valueOf(now),
        parentId);
  }

  /**
   * 自动归档候选：已完成且未归档的顶层任务（含项目归档策略字段）。
   *
   * <p>
   * {@code completeBefore} 为下限过滤（通常 now-1d），细粒度天数在 Service 按项目策略判断。
   */
  public List<Map<String, Object>> listAutoArchiveCandidates(LocalDateTime completeBefore, int limit) {
    int take = Math.min(Math.max(limit, 1), 200);
    return jdbc.query(
        """
            SELECT t.id AS id, t.complete_at AS completeAt, t.project_id AS projectId,
                   COALESCE(p.archive_method, 'system') AS archiveMethod,
                   COALESCE(p.archive_days, 30) AS archiveDays
            FROM bluedock_tasks t
            INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
            WHERE t.parent_id = 0
              AND t.deleted_at IS NULL
              AND t.archived_at IS NULL
              AND t.complete_at IS NOT NULL
              AND t.complete_at <= ?
              AND p.archived_at IS NULL
            ORDER BY t.complete_at ASC
            LIMIT ?
            """,
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", rs.getLong("id"));
          Timestamp c = rs.getTimestamp("completeAt");
          m.put("completeAt", c == null ? null : c.toLocalDateTime());
          m.put("projectId", rs.getLong("projectId"));
          m.put("archiveMethod", rs.getString("archiveMethod") == null ? "system" : rs.getString("archiveMethod"));
          m.put("archiveDays", rs.getInt("archiveDays"));
          return m;
        },
        Timestamp.valueOf(completeBefore),
        take);
  }

  /** 未领取主任务（无 bluedock_task_users 行）。 */
  public List<Map<String, Object>> listUnclaimedTasks(int limit) {
    int take = Math.min(Math.max(limit, 1), 500);
    return jdbc.query(
        """
            SELECT t.id AS id, t.name AS name, t.project_id AS projectId, p.dialog_id AS dialogId
            FROM bluedock_tasks t
            INNER JOIN bluedock_projects p ON p.id = t.project_id
              AND p.deleted_at IS NULL AND p.archived_at IS NULL AND p.dialog_id > 0
            WHERE t.parent_id = 0
              AND t.deleted_at IS NULL
              AND t.archived_at IS NULL
              AND t.complete_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM bluedock_task_users u WHERE u.task_id = t.id)
            ORDER BY t.project_id ASC, t.id ASC
            LIMIT ?
            """,
        (rs, i) -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", rs.getLong("id"));
          m.put("name", rs.getString("name") == null ? "" : rs.getString("name"));
          m.put("projectId", rs.getLong("projectId"));
          m.put("dialogId", rs.getLong("dialogId"));
          return m;
        },
        take);
  }

  private static Timestamp toTs(LocalDateTime v) {
    return v == null ? null : Timestamp.valueOf(v);
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private static TaskItem mapTeamRow(ResultSet rs, int rowNum) throws SQLException {
    TaskItem t = mapRow(rs, rowNum);
    long owner = rs.getLong("owner_user_id");
    if (!rs.wasNull()) {
      t.setOwnerUserId(owner);
    }
    return t;
  }

  private static TaskItem mapRow(ResultSet rs, int rowNum) throws SQLException {
    TaskItem t = new TaskItem();
    t.setId(rs.getLong("id"));
    t.setParentId(rs.getLong("parent_id"));
    t.setProjectId(rs.getLong("project_id"));
    t.setColumnId(rs.getLong("column_id"));
    t.setDialogId(rs.getLong("dialog_id"));
    t.setName(rs.getString("name"));
    t.setColor(rs.getString("color"));
    t.setDescription(rs.getString("description"));
    Timestamp start = rs.getTimestamp("start_at");
    if (start != null) {
      t.setStartAt(start.toLocalDateTime());
    }
    Timestamp end = rs.getTimestamp("end_at");
    if (end != null) {
      t.setEndAt(end.toLocalDateTime());
    }
    Timestamp complete = rs.getTimestamp("complete_at");
    if (complete != null) {
      t.setCompleteAt(complete.toLocalDateTime());
    }
    t.setVisibility(rs.getInt("visibility"));
    t.setPriorityLevel(rs.getInt("priority_level"));
    t.setPriorityName(rs.getString("priority_name"));
    t.setPriorityColor(rs.getString("priority_color"));
    t.setFlowItemId(rs.getLong("flow_item_id"));
    t.setFlowItemName(rs.getString("flow_item_name"));
    t.setSort(rs.getInt("sort"));
    t.setLoop(rs.getInt("loop"));
    Timestamp loopAt = rs.getTimestamp("loop_at");
    if (loopAt != null) {
      t.setLoopAt(loopAt.toLocalDateTime());
    }
    t.setUserId(rs.getLong("user_id"));
    Timestamp archived = rs.getTimestamp("archived_at");
    if (archived != null) {
      t.setArchivedAt(archived.toLocalDateTime());
    }
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      t.setDeletedAt(deleted.toLocalDateTime());
    }
    Timestamp created = rs.getTimestamp("created_at");
    if (created != null) {
      t.setCreatedAt(created.toLocalDateTime());
    }
    Timestamp updated = rs.getTimestamp("updated_at");
    if (updated != null) {
      t.setUpdatedAt(updated.toLocalDateTime());
    }
    return t;
  }

  /**
   * 会员参与任务列表（含协助）；未归档、未删除。
   *
   * @param projectIdsRestrict   非空时限定项目；null 表示不限制；空列表表示无结果
   * @param visibilityPublicOnly true 时仅 visibility=1
   * @param status               null / completed / uncompleted
   */
  public List<UserTaskRow> listForUser(
      long userId,
      Integer owner,
      Long projectId,
      String nameKeyword,
      String status,
      List<Long> projectIdsRestrict,
      boolean visibilityPublicOnly,
      int offset,
      int limit) {
    if (projectIdsRestrict != null && projectIdsRestrict.isEmpty()) {
      return List.of();
    }
    StringBuilder sql = new StringBuilder(
        """
            SELECT t.id, t.parent_id, t.project_id, t.column_id, t.dialog_id, t.name, t.color, t.description,
                   t.start_at, t.end_at, t.complete_at, t.visibility, t.priority_level, t.priority_name, t.priority_color,
                   t.flow_item_id, t.flow_item_name, t.sort, t.`loop`, t.loop_at, t.user_id,
                   t.archived_at, t.deleted_at, t.created_at, t.updated_at,
                   tu.owner AS join_owner,
                   COALESCE(p.name, '') AS project_name
            FROM bluedock_tasks t
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ?
            INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
            WHERE t.deleted_at IS NULL AND t.archived_at IS NULL
            """);
    java.util.ArrayList<Object> args = new java.util.ArrayList<>();
    args.add(userId);
    appendUserTaskFilters(
        sql, args, owner, projectId, nameKeyword, status, projectIdsRestrict, visibilityPublicOnly);
    sql.append(" ORDER BY t.id DESC LIMIT ? OFFSET ?");
    args.add(limit);
    args.add(offset);
    return jdbc.query(
        sql.toString(),
        (rs, i) -> {
          TaskItem t = mapRow(rs, i);
          return new UserTaskRow(t, rs.getInt("join_owner"), rs.getString("project_name"));
        },
        args.toArray());
  }

  public long countForUser(
      long userId,
      Integer owner,
      Long projectId,
      String nameKeyword,
      String status,
      List<Long> projectIdsRestrict,
      boolean visibilityPublicOnly) {
    if (projectIdsRestrict != null && projectIdsRestrict.isEmpty()) {
      return 0L;
    }
    StringBuilder sql = new StringBuilder(
        """
            SELECT COUNT(1)
            FROM bluedock_tasks t
            INNER JOIN bluedock_task_users tu ON tu.task_id = t.id AND tu.user_id = ?
            INNER JOIN bluedock_projects p ON p.id = t.project_id AND p.deleted_at IS NULL
            WHERE t.deleted_at IS NULL AND t.archived_at IS NULL
            """);
    java.util.ArrayList<Object> args = new java.util.ArrayList<>();
    args.add(userId);
    appendUserTaskFilters(
        sql, args, owner, projectId, nameKeyword, status, projectIdsRestrict, visibilityPublicOnly);
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  private static void appendUserTaskFilters(
      StringBuilder sql,
      java.util.ArrayList<Object> args,
      Integer owner,
      Long projectId,
      String nameKeyword,
      String status,
      List<Long> projectIdsRestrict,
      boolean visibilityPublicOnly) {
    if (owner != null) {
      sql.append(" AND tu.owner = ?");
      args.add(owner);
    }
    if (projectId != null && projectId > 0) {
      sql.append(" AND t.project_id = ?");
      args.add(projectId);
    }
    if (projectIdsRestrict != null) {
      String placeholders = projectIdsRestrict.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
      sql.append(" AND t.project_id IN (").append(placeholders).append(")");
      args.addAll(projectIdsRestrict);
    }
    if (visibilityPublicOnly) {
      sql.append(" AND t.visibility = 1");
    }
    if (nameKeyword != null && !nameKeyword.isBlank()) {
      String like = "%"
          + nameKeyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_")
          + "%";
      sql.append(" AND (t.name LIKE ? ESCAPE '!' OR IFNULL(t.description,'') LIKE ? ESCAPE '!')");
      args.add(like);
      args.add(like);
    }
    if ("completed".equalsIgnoreCase(status)) {
      sql.append(" AND t.complete_at IS NOT NULL");
    } else if ("uncompleted".equalsIgnoreCase(status)) {
      sql.append(" AND t.complete_at IS NULL");
    }
  }

  public record UserTaskRow(TaskItem task, int owner, String projectName) {
  }
}
