package com.bluedock.common.project;

import java.util.Collection;

/** 团队项目 ↔ 项目群；由 messenger 实现，project 可选注入。 */
public interface ProjectGroupBridge {
  /** 确保项目群存在并同步成员；返回 dialogId。个人项目不应调用。 */
  long ensureGroup(long projectId, String name, long ownerUserId, Collection<Long> memberIds);

  void syncMembers(long dialogId, Collection<Long> memberIds);

  /** 项目软删时解散对应群。 */
  void disbandByLink(long projectId);
}
