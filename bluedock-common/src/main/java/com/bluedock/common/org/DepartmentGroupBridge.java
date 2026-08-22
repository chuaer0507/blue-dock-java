package com.bluedock.common.org;

import java.util.Collection;

/** 部门 ↔ 部门群；由 messenger 实现，org 可选注入。 */
public interface DepartmentGroupBridge {
  /** 确保部门群存在并同步成员；返回 dialogId。 */
  long ensureGroup(long departmentId, String name, long ownerUserId, Collection<Long> memberIds);

  void syncMembers(long dialogId, Collection<Long> memberIds);
}
