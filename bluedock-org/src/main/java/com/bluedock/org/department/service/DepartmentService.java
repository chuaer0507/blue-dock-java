package com.bluedock.org.department.service;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.org.DepartmentGroupBridge;
import com.bluedock.org.department.domain.Department;
import com.bluedock.org.department.repo.DepartmentRepository;
import com.bluedock.system.service.AdminGuard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {
  private static final Pattern NAME_SPECIAL = Pattern.compile("[~!@#$%^&*()+\\-_=.:?<>,]");
  private static final int MAX_DEPTS = 200;

  private final DepartmentRepository departments;
  private final UserAccountRepository users;
  private final AdminGuard adminGuard;
  private final DepartmentGroupBridge groupBridge;

  public DepartmentService(
      DepartmentRepository departments,
      UserAccountRepository users,
      AdminGuard adminGuard,
      @Autowired(required = false) DepartmentGroupBridge groupBridge) {
    this.departments = departments;
    this.users = users;
    this.adminGuard = adminGuard;
    this.groupBridge = groupBridge;
  }

  public List<Map<String, Object>> list() {
    adminGuard.requireAdmin();
    List<Map<String, Object>> out = new ArrayList<>();
    for (Department d : departments.listAll()) {
      out.add(toView(d));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> add(
      Long id, String name, Long parentId, Long ownerUserId) {
    adminGuard.requireAdmin();
    String n = name == null ? "" : name.trim();
    if (n.length() < 2 || n.length() > 20 || NAME_SPECIAL.matcher(n).find() || n.contains("(M)")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_NAME_INVALID);
    }
    long resolvedParentId = parentId == null ? 0 : parentId;
    long owner = ownerUserId == null ? 0 : ownerUserId;
    if (owner <= 0 || users.findByUserId(owner).isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_OWNER_INVALID);
    }
    if (resolvedParentId > 0) {
      Department parent =
          departments
              .find(resolvedParentId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEPT_PARENT_INVALID));
      if (departments.depthOf(parent.getId()) >= 3) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_DEPTH);
      }
      if (departments.countByParent(resolvedParentId) >= 20) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_LIMIT);
      }
    }
    long deptId;
    if (id != null && id > 0) {
      Department exist =
          departments
              .find(id)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEPT_NOT_FOUND));
      if (resolvedParentId > 0
          && (resolvedParentId == id || departments.existsChild(id, resolvedParentId))) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_PARENT_INVALID);
      }
      if (owner != exist.getOwnerUserId() && departments.countOwnedBy(owner) >= 10) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_LIMIT);
      }
      departments.update(id, n, resolvedParentId, owner);
      deptId = id;
    } else {
      if (departments.count() >= MAX_DEPTS) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_LIMIT);
      }
      if (departments.countOwnedBy(owner) >= 10) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_LIMIT);
      }
      deptId = departments.insert(n, resolvedParentId, owner);
    }
    departments.addMember(deptId, owner);
    ensureDeptGroup(deptId);
    return toView(departments.find(deptId).orElseThrow());
  }

  @Transactional
  public void addDeputy(Long id, Long userId) {
    adminGuard.requireAdmin();
    if (id == null || id <= 0 || userId == null || userId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_OWNER_INVALID);
    }
    departments
        .find(id)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEPT_NOT_FOUND));
    if (users.findByUserId(userId).isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_OWNER_INVALID);
    }
    departments.addDeputy(id, userId);
    departments.addMember(id, userId);
    ensureDeptGroup(id);
  }

  @Transactional
  public void delDeputy(Long id, Long userId) {
    adminGuard.requireAdmin();
    if (id == null || id <= 0 || userId == null || userId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_OWNER_INVALID);
    }
    departments
        .find(id)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEPT_NOT_FOUND));
    departments.delDeputy(id, userId);
    // 罢免后移出部门（负责人除外）
    departments
        .find(id)
        .ifPresent(
            d -> {
              if (d.getOwnerUserId() != userId) {
                departments.removeMember(id, userId);
              }
            });
    ensureDeptGroup(id);
  }

  @Transactional
  public void delete(Long id) {
    adminGuard.requireAdmin();
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_NOT_FOUND);
    }
    departments
        .find(id)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEPT_NOT_FOUND));
    if (departments.countByParent(id) > 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_HAS_CHILDREN);
    }
    departments.delete(id);
  }

  @Transactional
  public Map<String, Object> sync(Long id) {
    adminGuard.requireAdmin();
    if (id == null || id <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DEPT_NOT_FOUND);
    }
    departments
        .find(id)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DEPT_NOT_FOUND));
    List<Long> subIds = departments.collectSubIds(id);
    if (subIds.isEmpty()) {
      return Map.of(
          "syncedCount",
          0,
          "alreadyInDeptCount",
          0,
          "skippedDisabledCount",
          0,
          "subDepartmentIds",
          List.of());
    }
    int synced = 0;
    int already = 0;
    int skippedDisabled = 0;
    for (Long subId : subIds) {
      for (Long userId : departments.listActiveMemberIds(subId)) {
        if (departments.isMember(id, userId)) {
          already++;
        } else {
          departments.addMember(id, userId);
          synced++;
        }
      }
      // 统计跳过的禁用/机器人：子部门全量 - 活跃
      int all = departments.listMemberIds(subId).size();
      int active = departments.listActiveMemberIds(subId).size();
      skippedDisabled += Math.max(0, all - active);
    }
    ensureDeptGroup(id);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("syncedCount", synced);
    out.put("alreadyInDeptCount", already);
    out.put("skippedDisabledCount", skippedDisabled);
    out.put("subDepartmentIds", subIds);
    return out;
  }

  private void ensureDeptGroup(long deptId) {
    if (groupBridge == null) {
      return;
    }
    Department d = departments.find(deptId).orElse(null);
    if (d == null) {
      return;
    }
    Set<Long> members = new HashSet<>(departments.listMemberIds(deptId));
    members.add(d.getOwnerUserId());
    members.addAll(d.getDeputyUserIds());
    long dialogId = groupBridge.ensureGroup(deptId, d.getName(), d.getOwnerUserId(), members);
    if (d.getDialogId() != dialogId) {
      departments.updateDialogId(deptId, dialogId);
    }
  }

  public List<Map<String, Object>> myDepartments() {
    long userId = AuthContext.requireUserId();
    List<Map<String, Object>> out = new ArrayList<>();
    int take = 0;
    for (Long deptId : departments.listDepartmentIdsOfUser(userId)) {
      if (take >= 10) {
        break;
      }
      departments
          .find(deptId)
          .ifPresent(
              d ->
                  out.add(
                      Map.of(
                          "id",
                          d.getId(),
                          "name",
                          d.getName(),
                          "ownerUserId",
                          d.getOwnerUserId())));
      take++;
    }
    return out;
  }

  public List<Map<String, Object>> managedDepartments() {
    long userId = AuthContext.requireUserId();
    List<Map<String, Object>> out = new ArrayList<>();
    for (Long deptId : departments.listManagedDepartmentIds(userId)) {
      departments
          .find(deptId)
          .ifPresent(
              d ->
                  out.add(
                      Map.of(
                          "id",
                          d.getId(),
                          "name",
                          d.getName(),
                          "ownerUserId",
                          d.getOwnerUserId())));
    }
    return out;
  }

  private static Map<String, Object> toView(Department d) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", d.getId());
    m.put("name", d.getName());
    m.put("parentId", d.getParentId());
    m.put("ownerUserId", d.getOwnerUserId());
    m.put("dialogId", d.getDialogId());
    m.put("deputyUserIds", d.getDeputyUserIds());
    return m;
  }
}
