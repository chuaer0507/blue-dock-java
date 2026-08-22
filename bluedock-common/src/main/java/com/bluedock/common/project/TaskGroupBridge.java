package com.bluedock.common.project;

import java.util.Collection;

/** 任务 ↔ 任务群；由 messenger 实现，task 可选注入。按需创建。 */
public interface TaskGroupBridge {
  /** 确保任务群存在并同步成员；返回 dialogId。 */
  long ensureGroup(long taskId, String name, long ownerUserId, Collection<Long> memberIds);

  void syncMembers(long dialogId, Collection<Long> memberIds);

  void disbandByLink(long taskId);
}
