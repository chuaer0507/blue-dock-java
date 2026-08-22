package com.bluedock.project.repo;

import com.bluedock.project.domain.Project;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {
  private static final RowMapper<Project> MAPPER = ProjectRepository::mapRow;

  private final JdbcTemplate jdbc;

  public ProjectRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(Project p) {
    jdbc.update(
        """
        INSERT INTO bluedock_projects
          (id, name, description, user_id, is_personal, dialog_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        p.getId(),
        p.getName(),
        p.getDescription(),
        p.getUserId(),
        p.getIsPersonal(),
        p.getDialogId(),
        Timestamp.valueOf(p.getCreatedAt()),
        Timestamp.valueOf(p.getUpdatedAt()));
  }

  public void update(Project p) {
    jdbc.update(
        """
        UPDATE bluedock_projects
        SET name = ?, description = ?, archive_method = ?, archive_days = ?,
            ai_auto_analyze = ?, department_owner_view = ?, task_template_share = ?,
            updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        p.getName(),
        p.getDescription(),
        p.getArchiveMethod(),
        p.getArchiveDays(),
        p.getAiAutoAnalyze(),
        p.getDepartmentOwnerView(),
        p.getTaskTemplateShare(),
        Timestamp.valueOf(LocalDateTime.now()),
        p.getId());
  }

  public void updateDialogId(long projectId, long dialogId) {
    jdbc.update(
        """
        UPDATE bluedock_projects SET dialog_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        dialogId,
        Timestamp.valueOf(LocalDateTime.now()),
        projectId);
  }

  public Optional<Project> findActive(long id) {
    var list =
        jdbc.query(
            """
            SELECT id, name, description, user_id, is_personal, dialog_id,
                   archive_method, archive_days, ai_auto_analyze,
                   department_owner_view, task_template_share,
                   archived_at, created_at, updated_at, deleted_at,
                   0 AS my_owner, CAST(NULL AS DATETIME(3)) AS my_top_at
            FROM bluedock_projects
            WHERE id = ? AND deleted_at IS NULL
            """,
            MAPPER,
            id);
    return list.stream().findFirst();
  }

  public List<Project> listForUser(long userId, boolean includeArchived) {
    return listForUser(userId, includeArchived ? "all" : "no", "all", null);
  }

  /**
   * @param archived no=未归档 · yes=仅归档 · all=全部
   * @param type all=全部 · team=团队 · personal=个人
   * @param nameKeyword 名称模糊匹配；空则不过滤
   */
  public List<Project> listForUser(
      long userId, String archived, String type, String nameKeyword) {
    return listForUser(userId, archived, type, nameKeyword, null, 0, 0);
  }

  /**
   * @param projectIdsRestrict 非空时限定项目 ID；空列表返回空
   * @param limit ≤0 表示不分页；offset 仅在 limit&gt;0 时生效
   */
  public List<Project> listForUser(
      long userId,
      String archived,
      String type,
      String nameKeyword,
      List<Long> projectIdsRestrict,
      int offset,
      int limit) {
    if (projectIdsRestrict != null && projectIdsRestrict.isEmpty()) {
      return List.of();
    }
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT p.id, p.name, p.description, p.user_id, p.is_personal, p.dialog_id,
                   p.archive_method, p.archive_days, p.ai_auto_analyze,
                   p.department_owner_view, p.task_template_share,
                   p.archived_at, p.created_at, p.updated_at, p.deleted_at,
                   pu.owner AS my_owner, pu.top_at AS my_top_at
            FROM bluedock_projects p
            INNER JOIN bluedock_project_users pu ON pu.project_id = p.id AND pu.user_id = ?
            WHERE p.deleted_at IS NULL
            """);
    List<Object> args = new ArrayList<>();
    args.add(userId);

    String arch = archived == null ? "no" : archived.trim().toLowerCase();
    if ("yes".equals(arch)) {
      sql.append(" AND p.archived_at IS NOT NULL");
    } else if (!"all".equals(arch)) {
      sql.append(" AND p.archived_at IS NULL");
    }

    String t = type == null ? "all" : type.trim().toLowerCase();
    if ("team".equals(t)) {
      sql.append(" AND p.is_personal = 0");
    } else if ("personal".equals(t)) {
      sql.append(" AND p.is_personal = 1");
    }

    if (nameKeyword != null && !nameKeyword.isBlank()) {
      sql.append(" AND p.name LIKE ? ESCAPE '!'");
      args.add("%" + escapeLike(nameKeyword.trim()) + "%");
    }

    if (projectIdsRestrict != null) {
      String placeholders =
          projectIdsRestrict.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
      sql.append(" AND p.id IN (").append(placeholders).append(")");
      args.addAll(projectIdsRestrict);
    }

    sql.append(
        " ORDER BY pu.top_at IS NULL ASC, pu.top_at DESC, pu.sort ASC, p.id DESC");
    if (limit > 0) {
      sql.append(" LIMIT ? OFFSET ?");
      args.add(limit);
      args.add(Math.max(0, offset));
    }
    return jdbc.query(sql.toString(), MAPPER, args.toArray());
  }

  /** 与 {@link #listForUser} 同过滤条件的计数。 */
  public long countForUser(
      long userId,
      String archived,
      String type,
      String nameKeyword,
      List<Long> projectIdsRestrict) {
    if (projectIdsRestrict != null && projectIdsRestrict.isEmpty()) {
      return 0L;
    }
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(1)
            FROM bluedock_projects p
            INNER JOIN bluedock_project_users pu ON pu.project_id = p.id AND pu.user_id = ?
            WHERE p.deleted_at IS NULL
            """);
    List<Object> args = new ArrayList<>();
    args.add(userId);
    String arch = archived == null ? "no" : archived.trim().toLowerCase();
    if ("yes".equals(arch)) {
      sql.append(" AND p.archived_at IS NOT NULL");
    } else if (!"all".equals(arch)) {
      sql.append(" AND p.archived_at IS NULL");
    }
    String t = type == null ? "all" : type.trim().toLowerCase();
    if ("team".equals(t)) {
      sql.append(" AND p.is_personal = 0");
    } else if ("personal".equals(t)) {
      sql.append(" AND p.is_personal = 1");
    }
    if (nameKeyword != null && !nameKeyword.isBlank()) {
      sql.append(" AND p.name LIKE ? ESCAPE '!'");
      args.add("%" + escapeLike(nameKeyword.trim()) + "%");
    }
    if (projectIdsRestrict != null) {
      String placeholders =
          projectIdsRestrict.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
      sql.append(" AND p.id IN (").append(placeholders).append(")");
      args.addAll(projectIdsRestrict);
    }
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  private static String escapeLike(String raw) {
    return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  public List<Long> listProjectIdsForUser(long userId) {
    return jdbc.query(
        """
        SELECT p.id FROM bluedock_projects p
        INNER JOIN bluedock_project_users pu ON pu.project_id = p.id AND pu.user_id = ?
        WHERE p.deleted_at IS NULL AND p.archived_at IS NULL
        """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  public int countPersonalForUser(long userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_projects
            WHERE user_id = ? AND is_personal = 1 AND deleted_at IS NULL
            """,
            Integer.class,
            userId);
    return n == null ? 0 : n;
  }

  public void insertMember(long id, long projectId, long userId, int owner) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_project_users (id, project_id, user_id, owner, sort, created_at, updated_at)
        VALUES (?, ?, ?, ?, 0, ?, ?)
        """,
        id,
        projectId,
        userId,
        owner,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
  }

  public void updateMemberOwner(long projectId, long userId, int owner) {
    jdbc.update(
        """
        UPDATE bluedock_project_users
        SET owner = ?, updated_at = ?
        WHERE project_id = ? AND user_id = ?
        """,
        owner,
        Timestamp.valueOf(LocalDateTime.now()),
        projectId,
        userId);
  }

  public void deleteMember(long projectId, long userId) {
    jdbc.update(
        "DELETE FROM bluedock_project_users WHERE project_id = ? AND user_id = ?", projectId, userId);
  }

  public List<Long> listMemberUserIds(long projectId) {
    return jdbc.query(
        "SELECT user_id FROM bluedock_project_users WHERE project_id = ? ORDER BY owner DESC, user_id ASC",
        (rs, i) -> rs.getLong(1),
        projectId);
  }

  public Optional<Long> findOwnerUserId(long projectId) {
    var list =
        jdbc.query(
            """
            SELECT user_id FROM bluedock_project_users
            WHERE project_id = ? AND owner = ?
            LIMIT 1
            """,
            (rs, i) -> rs.getLong(1),
            projectId,
            1);
    return list.stream().findFirst();
  }

  /** 用户作为项目负责人（owner=1）的未删除项目。 */
  public List<Long> listOwnedProjectIds(long userId) {
    return jdbc.query(
        """
        SELECT pu.project_id
        FROM bluedock_project_users pu
        INNER JOIN bluedock_projects p ON p.id = pu.project_id
        WHERE pu.user_id = ? AND pu.owner = 1 AND p.deleted_at IS NULL
        """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  /** 用户参与的未删除项目。 */
  public List<Long> listMemberProjectIds(long userId) {
    return jdbc.query(
        """
        SELECT pu.project_id
        FROM bluedock_project_users pu
        INNER JOIN bluedock_projects p ON p.id = pu.project_id
        WHERE pu.user_id = ? AND p.deleted_at IS NULL
        """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  public void updateProjectCreator(long projectId, long userId) {
    jdbc.update(
        """
        UPDATE bluedock_projects SET user_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        userId,
        Timestamp.valueOf(LocalDateTime.now()),
        projectId);
  }

  public void updateIsPersonal(long projectId, int isPersonal) {
    jdbc.update(
        """
        UPDATE bluedock_projects SET is_personal = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        isPersonal,
        Timestamp.valueOf(LocalDateTime.now()),
        projectId);
  }

  /** 当前用户作为负责人/管理员（owner≥1）的未归档项目。无 departmentId 时作团队仪表盘回退范围。 */
  public List<Long> listManagedProjectIds(long userId) {
    return jdbc.query(
        """
        SELECT pu.project_id
        FROM bluedock_project_users pu
        INNER JOIN bluedock_projects p ON p.id = pu.project_id
        WHERE pu.user_id = ? AND pu.owner >= 1
          AND p.deleted_at IS NULL AND p.archived_at IS NULL
        """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  /**
   * 部门负责人视角：成员参与且允许负责人视角可见的未归档项目。
   */
  public List<Long> listProjectIdsForDepartmentOwnerView(List<Long> memberUserIds) {
    if (memberUserIds == null || memberUserIds.isEmpty()) {
      return List.of();
    }
    String placeholders =
        memberUserIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    Object[] args = memberUserIds.toArray();
    return jdbc.query(
        """
        SELECT DISTINCT p.id
        FROM bluedock_projects p
        INNER JOIN bluedock_project_users pu ON pu.project_id = p.id
        WHERE pu.user_id IN (%s)
          AND IFNULL(p.department_owner_view, 1) = 1
          AND p.deleted_at IS NULL AND p.archived_at IS NULL
        ORDER BY p.id DESC
        """
            .formatted(placeholders),
        (rs, i) -> rs.getLong(1),
        args);
  }

  public List<Long> listDistinctMemberUserIds(List<Long> projectIds) {
    if (projectIds == null || projectIds.isEmpty()) {
      return List.of();
    }
    String placeholders = projectIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    Object[] args = projectIds.toArray();
    return jdbc.query(
        "SELECT DISTINCT user_id FROM bluedock_project_users WHERE project_id IN (" + placeholders + ") ORDER BY user_id",
        (rs, i) -> rs.getLong(1),
        args);
  }

  /** 目标用户参与、未归档、且允许部门负责人视角可见的项目。 */
  public List<Long> listProjectIdsForUserOwnerView(long userId) {
    return jdbc.query(
        """
        SELECT DISTINCT p.id
        FROM bluedock_projects p
        INNER JOIN bluedock_project_users pu ON pu.project_id = p.id AND pu.user_id = ?
        WHERE p.deleted_at IS NULL
          AND IFNULL(p.department_owner_view, 1) = 1
        ORDER BY p.id DESC
        """,
        (rs, i) -> rs.getLong(1),
        userId);
  }

  /** 用户参与的未归档项目数；projectIdsRestrict 非空时限定集合。 */
  public long countProjectsForUser(long userId, List<Long> projectIdsRestrict) {
    if (projectIdsRestrict != null && projectIdsRestrict.isEmpty()) {
      return 0L;
    }
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(DISTINCT p.id)
            FROM bluedock_projects p
            INNER JOIN bluedock_project_users pu ON pu.project_id = p.id AND pu.user_id = ?
            WHERE p.deleted_at IS NULL AND p.archived_at IS NULL
            """);
    java.util.ArrayList<Object> args = new java.util.ArrayList<>();
    args.add(userId);
    if (projectIdsRestrict != null) {
      String placeholders =
          projectIdsRestrict.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
      sql.append(" AND p.id IN (").append(placeholders).append(")");
      args.addAll(projectIdsRestrict);
    }
    Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    return n == null ? 0L : n;
  }

  public Optional<Integer> findMemberOwner(long projectId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT owner FROM bluedock_project_users
            WHERE project_id = ? AND user_id = ?
            """,
            (rs, i) -> rs.getInt(1),
            projectId,
            userId);
    return list.stream().findFirst();
  }

  /**
   * 项目是否允许使用跨项目任务模板。缺省 / 非 close 视为 open。
   */
  public boolean isTemplateShareOpen(long projectId) {
    var list =
        jdbc.query(
            """
            SELECT task_template_share FROM bluedock_projects
            WHERE id = ? AND deleted_at IS NULL
            """,
            (rs, i) -> rs.getString(1),
            projectId);
    if (list.isEmpty()) {
      return true;
    }
    String raw = list.get(0);
    return raw == null || raw.isBlank() || !"close".equalsIgnoreCase(raw.trim());
  }

  /** 当前用户对项目的个人排序（仅影响本人）。 */
  public void updateMemberSort(long userId, long projectId, int sort) {
    jdbc.update(
        """
        UPDATE bluedock_project_users
        SET sort = ?, updated_at = ?
        WHERE user_id = ? AND project_id = ?
        """,
        sort,
        Timestamp.valueOf(LocalDateTime.now()),
        userId,
        projectId);
  }

  /** 切换置顶结果。 */
  public record TopToggle(LocalDateTime topAt) {}

  /**
   * 切换置顶：有 top_at 则清空，否则设为当前时间。
   *
   * @return empty 表示非成员
   */
  public Optional<TopToggle> toggleMemberTop(long userId, long projectId) {
    Integer exists =
        jdbc.query(
                """
                SELECT 1 FROM bluedock_project_users
                WHERE user_id = ? AND project_id = ?
                """,
                (rs, i) -> 1,
                userId,
                projectId)
            .stream()
            .findFirst()
            .orElse(null);
    if (exists == null) {
      return Optional.empty();
    }
    LocalDateTime existing =
        jdbc.query(
                """
                SELECT top_at FROM bluedock_project_users
                WHERE user_id = ? AND project_id = ?
                """,
                (rs, i) -> {
                  Timestamp ts = rs.getTimestamp(1);
                  return ts == null ? null : ts.toLocalDateTime();
                },
                userId,
                projectId)
            .stream()
            .findFirst()
            .orElse(null);
    LocalDateTime next = existing == null ? LocalDateTime.now() : null;
    jdbc.update(
        """
        UPDATE bluedock_project_users
        SET top_at = ?, updated_at = ?
        WHERE user_id = ? AND project_id = ?
        """,
        next == null ? null : Timestamp.valueOf(next),
        Timestamp.valueOf(LocalDateTime.now()),
        userId,
        projectId);
    jdbc.update(
        """
        UPDATE bluedock_projects SET updated_at = ? WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        projectId);
    return Optional.of(new TopToggle(next));
  }

  public Optional<LocalDateTime> findMemberTopAt(long projectId, long userId) {
    var list =
        jdbc.query(
            """
            SELECT top_at FROM bluedock_project_users
            WHERE project_id = ? AND user_id = ?
            """,
            (rs, i) -> {
              Timestamp ts = rs.getTimestamp(1);
              return ts == null ? null : ts.toLocalDateTime();
            },
            projectId,
            userId);
    if (list.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(list.get(0));
  }

  public void archive(long projectId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_projects
        SET archived_at = ?, archived_user_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(now),
        userId,
        Timestamp.valueOf(now),
        projectId);
  }

  public void unarchive(long projectId, long userId) {
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        UPDATE bluedock_projects
        SET archived_at = NULL, archived_user_id = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        userId,
        Timestamp.valueOf(now),
        projectId);
  }

  public void softDelete(long projectId) {
    jdbc.update(
        """
        UPDATE bluedock_projects
        SET deleted_at = ?, updated_at = ?
        WHERE id = ? AND deleted_at IS NULL
        """,
        Timestamp.valueOf(LocalDateTime.now()),
        Timestamp.valueOf(LocalDateTime.now()),
        projectId);
  }

  private static Project mapRow(ResultSet rs, int rowNum) throws SQLException {
    Project p = new Project();
    p.setId(rs.getLong("id"));
    p.setName(rs.getString("name"));
    p.setDescription(rs.getString("description"));
    p.setUserId(rs.getLong("user_id"));
    p.setIsPersonal(rs.getInt("is_personal"));
    p.setDialogId(rs.getLong("dialog_id"));
    String method = rs.getString("archive_method");
    if (method != null) {
      p.setArchiveMethod(method);
    }
    p.setArchiveDays(rs.getInt("archive_days"));
    String ai = rs.getString("ai_auto_analyze");
    if (ai != null) {
      p.setAiAutoAnalyze(ai);
    }
    p.setDepartmentOwnerView(rs.getInt("department_owner_view"));
    String share = rs.getString("task_template_share");
    if (share != null) {
      p.setTaskTemplateShare(share);
    }
    Timestamp archived = rs.getTimestamp("archived_at");
    if (archived != null) {
      p.setArchivedAt(archived.toLocalDateTime());
    }
    Timestamp created = rs.getTimestamp("created_at");
    if (created != null) {
      p.setCreatedAt(created.toLocalDateTime());
    }
    Timestamp updated = rs.getTimestamp("updated_at");
    if (updated != null) {
      p.setUpdatedAt(updated.toLocalDateTime());
    }
    Timestamp deleted = rs.getTimestamp("deleted_at");
    if (deleted != null) {
      p.setDeletedAt(deleted.toLocalDateTime());
    }
    p.setMyOwner(rs.getInt("my_owner"));
    Timestamp top = rs.getTimestamp("my_top_at");
    if (top != null) {
      p.setMyTopAt(top.toLocalDateTime());
    }
    return p;
  }
}
