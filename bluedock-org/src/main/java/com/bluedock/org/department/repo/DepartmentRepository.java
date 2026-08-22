package com.bluedock.org.department.repo;

import com.bluedock.common.util.IdGenerator;
import com.bluedock.org.department.domain.Department;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepository {
  private static final RowMapper<Department> MAPPER =
      (rs, i) -> {
        Department d = new Department();
        d.setId(rs.getLong("id"));
        d.setName(rs.getString("name"));
        d.setParentId(rs.getLong("parent_id"));
        d.setOwnerUserId(rs.getLong("owner_user_id"));
        d.setDialogId(rs.getLong("dialog_id"));
        Timestamp c = rs.getTimestamp("created_at");
        Timestamp u = rs.getTimestamp("updated_at");
        if (c != null) {
          d.setCreatedAt(c.toLocalDateTime());
        }
        if (u != null) {
          d.setUpdatedAt(u.toLocalDateTime());
        }
        return d;
      };

  private final JdbcTemplate jdbc;

  public DepartmentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Department> listAll() {
    List<Department> list =
        jdbc.query("SELECT * FROM bluedock_user_departments ORDER BY id", MAPPER);
    attachDeputies(list);
    return list;
  }

  public Optional<Department> find(long id) {
    var list = jdbc.query("SELECT * FROM bluedock_user_departments WHERE id = ?", MAPPER, id);
    if (list.isEmpty()) {
      return Optional.empty();
    }
    Department d = list.get(0);
    d.setDeputyUserIds(listDeputyIds(id));
    return Optional.of(d);
  }

  public int count() {
    Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM bluedock_user_departments", Integer.class);
    return n == null ? 0 : n;
  }

  public int countByParent(long parentId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_departments WHERE parent_id = ?",
            Integer.class,
            parentId);
    return n == null ? 0 : n;
  }

  public int countOwnedBy(long ownerUserId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_departments WHERE owner_user_id = ?",
            Integer.class,
            ownerUserId);
    return n == null ? 0 : n;
  }

  public boolean existsChild(long parentId, long childId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT COUNT(1) FROM bluedock_user_departments WHERE parent_id = ? AND id = ?",
            Integer.class,
            parentId,
            childId);
    return n != null && n > 0;
  }

  public long insert(String name, long parentId, long ownerUserId) {
    long id = IdGenerator.nextId();
    LocalDateTime now = LocalDateTime.now();
    jdbc.update(
        """
        INSERT INTO bluedock_user_departments
          (id, name, parent_id, owner_user_id, dialog_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, 0, ?, ?)
        """,
        id,
        name,
        parentId,
        ownerUserId,
        Timestamp.valueOf(now),
        Timestamp.valueOf(now));
    return id;
  }

  public void update(long id, String name, long parentId, long ownerUserId) {
    jdbc.update(
        """
        UPDATE bluedock_user_departments
        SET name = ?, parent_id = ?, owner_user_id = ?, updated_at = ?
        WHERE id = ?
        """,
        name,
        parentId,
        ownerUserId,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  public void updateDialogId(long id, long dialogId) {
    jdbc.update(
        """
        UPDATE bluedock_user_departments SET dialog_id = ?, updated_at = ? WHERE id = ?
        """,
        dialogId,
        Timestamp.valueOf(LocalDateTime.now()),
        id);
  }

  public void delete(long id) {
    jdbc.update("DELETE FROM bluedock_user_department_owners WHERE department_id = ?", id);
    jdbc.update("DELETE FROM bluedock_user_department_members WHERE department_id = ?", id);
    jdbc.update("DELETE FROM bluedock_user_departments WHERE id = ?", id);
  }

  public void addDeputy(long departmentId, long userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_user_department_owners
            WHERE department_id = ? AND user_id = ?
            """,
            Integer.class,
            departmentId,
            userId);
    if (n != null && n > 0) {
      return;
    }
    jdbc.update(
        """
        INSERT INTO bluedock_user_department_owners (id, department_id, user_id, created_at)
        VALUES (?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        departmentId,
        userId,
        Timestamp.valueOf(LocalDateTime.now()));
  }

  public void delDeputy(long departmentId, long userId) {
    jdbc.update(
        "DELETE FROM bluedock_user_department_owners WHERE department_id = ? AND user_id = ?",
        departmentId,
        userId);
  }

  public List<Long> listDeputyIds(long departmentId) {
    return jdbc.query(
        "SELECT user_id FROM bluedock_user_department_owners WHERE department_id = ?",
        (rs, i) -> rs.getLong(1),
        departmentId);
  }

  public void addMember(long departmentId, long userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_user_department_members
            WHERE department_id = ? AND user_id = ?
            """,
            Integer.class,
            departmentId,
            userId);
    if (n != null && n > 0) {
      return;
    }
    jdbc.update(
        """
        INSERT INTO bluedock_user_department_members (id, department_id, user_id, created_at)
        VALUES (?, ?, ?, ?)
        """,
        IdGenerator.nextId(),
        departmentId,
        userId,
        Timestamp.valueOf(LocalDateTime.now()));
  }

  public boolean isMember(long departmentId, long userId) {
    Integer n =
        jdbc.queryForObject(
            """
            SELECT COUNT(1) FROM bluedock_user_department_members
            WHERE department_id = ? AND user_id = ?
            """,
            Integer.class,
            departmentId,
            userId);
    return n != null && n > 0;
  }

  public void removeMember(long departmentId, long userId) {
    jdbc.update(
        "DELETE FROM bluedock_user_department_members WHERE department_id = ? AND user_id = ?",
        departmentId,
        userId);
  }

  public List<Long> listMemberIds(long departmentId) {
    return jdbc.query(
        "SELECT user_id FROM bluedock_user_department_members WHERE department_id = ?",
        (rs, i) -> rs.getLong(1),
        departmentId);
  }

  /** 部门在职、非机器人成员。 */
  public List<Long> listActiveMemberIds(long departmentId) {
    return jdbc.query(
        """
        SELECT m.user_id
        FROM bluedock_user_department_members m
        INNER JOIN bluedock_users u ON u.id = m.user_id
        WHERE m.department_id = ?
          AND u.disable_at IS NULL
          AND IFNULL(u.is_bot, 0) = 0
        ORDER BY m.user_id
        """,
        (rs, i) -> rs.getLong(1),
        departmentId);
  }

  public List<Long> listDepartmentIdsOfUser(long userId) {
    return jdbc.query(
        "SELECT department_id FROM bluedock_user_department_members WHERE user_id = ?",
        (rs, i) -> rs.getLong(1),
        userId);
  }

  public List<Long> listManagedDepartmentIds(long userId) {
    List<Long> ids = new ArrayList<>();
    ids.addAll(
        jdbc.query(
            "SELECT id FROM bluedock_user_departments WHERE owner_user_id = ?",
            (rs, i) -> rs.getLong(1),
            userId));
    ids.addAll(
        jdbc.query(
            "SELECT department_id FROM bluedock_user_department_owners WHERE user_id = ?",
            (rs, i) -> rs.getLong(1),
            userId));
    return ids.stream().distinct().toList();
  }

  /** 用户担任负责人的部门。 */
  public List<Long> listOwnedDepartmentIds(long userId) {
    return jdbc.query(
        "SELECT id FROM bluedock_user_departments WHERE owner_user_id = ?",
        (rs, i) -> rs.getLong(1),
        userId);
  }

  public void removeAllDeputiesForUser(long userId) {
    jdbc.update("DELETE FROM bluedock_user_department_owners WHERE user_id = ?", userId);
  }

  public void removeAllMembershipsForUser(long userId) {
    jdbc.update("DELETE FROM bluedock_user_department_members WHERE user_id = ?", userId);
  }

  public List<Long> listChildIds(long parentId) {
    return jdbc.query(
        "SELECT id FROM bluedock_user_departments WHERE parent_id = ?",
        (rs, i) -> rs.getLong(1),
        parentId);
  }

  public List<Long> collectSubIds(long rootId) {
    List<Long> all = new ArrayList<>();
    List<Long> frontier = listChildIds(rootId);
    while (!frontier.isEmpty()) {
      all.addAll(frontier);
      List<Long> next = new ArrayList<>();
      for (Long id : frontier) {
        next.addAll(listChildIds(id));
      }
      frontier = next;
    }
    return all;
  }

  /** 根部门 + 全部下级。 */
  public List<Long> listTreeIds(long rootId) {
    List<Long> ids = new ArrayList<>();
    ids.add(rootId);
    ids.addAll(collectSubIds(rootId));
    return ids;
  }

  /**
   * 部门树内在职、非机器人成员（去重）。
   */
  public List<Long> listActiveMemberIdsInTree(long rootDepartmentId) {
    List<Long> deptIds = listTreeIds(rootDepartmentId);
    if (deptIds.isEmpty()) {
      return List.of();
    }
    String placeholders = deptIds.stream().map(x -> "?").collect(java.util.stream.Collectors.joining(","));
    Object[] args = deptIds.toArray();
    return jdbc.query(
        """
        SELECT DISTINCT m.user_id
        FROM bluedock_user_department_members m
        INNER JOIN bluedock_users u ON u.id = m.user_id
        WHERE m.department_id IN (%s)
          AND u.disable_at IS NULL
          AND IFNULL(u.is_bot, 0) = 0
        ORDER BY m.user_id
        """
            .formatted(placeholders),
        (rs, i) -> rs.getLong(1),
        args);
  }

  public boolean canManage(long userId, long departmentId) {
    return listManagedDepartmentIds(userId).contains(departmentId);
  }

  /** 当前用户作为负责人/副负责人可管理的全部在职非机器人成员（含各管理树）。 */
  public List<Long> listManagedMemberUserIds(long managerUserId) {
    List<Long> deptIds = listManagedDepartmentIds(managerUserId);
    if (deptIds.isEmpty()) {
      return List.of();
    }
    java.util.LinkedHashSet<Long> members = new java.util.LinkedHashSet<>();
    for (Long deptId : deptIds) {
      members.addAll(listActiveMemberIdsInTree(deptId));
    }
    return List.copyOf(members);
  }

  public int depthOf(long id) {
    int depth = 0;
    long cur = id;
    while (cur > 0 && depth < 10) {
      Optional<Department> d = find(cur);
      if (d.isEmpty()) {
        break;
      }
      cur = d.get().getParentId();
      depth++;
    }
    return depth;
  }

  private void attachDeputies(List<Department> list) {
    if (list.isEmpty()) {
      return;
    }
    Map<Long, List<Long>> map = new HashMap<>();
    jdbc.query(
        "SELECT department_id, user_id FROM bluedock_user_department_owners",
        rs -> {
          long deptId = rs.getLong(1);
          long userId = rs.getLong(2);
          map.computeIfAbsent(deptId, k -> new ArrayList<>()).add(userId);
        });
    for (Department d : list) {
      d.setDeputyUserIds(map.getOrDefault(d.getId(), List.of()));
    }
  }
}
