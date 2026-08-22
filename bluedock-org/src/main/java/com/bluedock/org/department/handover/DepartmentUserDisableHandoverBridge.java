package com.bluedock.org.department.handover;

import com.bluedock.common.org.DepartmentGroupBridge;
import com.bluedock.common.user.UserDisableHandoverBridge;
import com.bluedock.org.department.domain.Department;
import com.bluedock.org.department.repo.DepartmentRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 离职交接 — 部门：负责人迁给交接人；清除离职用户的部门管理员与成员身份；同步部门群。
 */
@Component
@Order(30)
public class DepartmentUserDisableHandoverBridge implements UserDisableHandoverBridge {
  private final DepartmentRepository departments;
  private final DepartmentGroupBridge groupBridge;

  public DepartmentUserDisableHandoverBridge(
      DepartmentRepository departments,
      @Autowired(required = false) DepartmentGroupBridge groupBridge) {
    this.departments = departments;
    this.groupBridge = groupBridge;
  }

  @Override
  @Transactional
  public void handover(long fromUserId, long toUserId) {
    if (fromUserId == toUserId) {
      return;
    }
    List<Long> owned = departments.listOwnedDepartmentIds(fromUserId);
    for (Long deptId : owned) {
      Department d = departments.find(deptId).orElse(null);
      if (d == null) {
        continue;
      }
      departments.update(deptId, d.getName(), d.getParentId(), toUserId);
      departments.addMember(deptId, toUserId);
      departments.delDeputy(deptId, toUserId);
      ensureDeptGroup(deptId);
    }
    departments.removeAllDeputiesForUser(fromUserId);
    departments.removeAllMembershipsForUser(fromUserId);
    for (Long deptId : owned) {
      ensureDeptGroup(deptId);
    }
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
}
