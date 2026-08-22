package com.bluedock.task.handover;

import com.bluedock.common.user.UserDisableHandoverBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.task.repo.TaskRepository;
import com.bluedock.task.repo.TaskVisibilityUserRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 离职交接 — 任务：负责迁给交接人；清除离职用户的任务成员与可见名单。
 */
@Component
@Order(20)
public class TaskUserDisableHandoverBridge implements UserDisableHandoverBridge {
  private final TaskRepository tasks;
  private final TaskVisibilityUserRepository visibility;

  public TaskUserDisableHandoverBridge(
      TaskRepository tasks, TaskVisibilityUserRepository visibility) {
    this.tasks = tasks;
    this.visibility = visibility;
  }

  @Override
  @Transactional
  public void handover(long fromUserId, long toUserId) {
    if (fromUserId == toUserId) {
      return;
    }
    for (Long taskId : tasks.listTaskIdsOwnedBy(fromUserId)) {
      transferOwner(taskId, fromUserId, toUserId);
    }
    tasks.deleteAllAssigneesForUser(fromUserId);
    visibility.deleteByUser(fromUserId);
  }

  private void transferOwner(long taskId, long fromUserId, long toUserId) {
    var row = tasks.findAssigneeRow(taskId, fromUserId);
    if (row.isEmpty()) {
      return;
    }
    long parentTaskId = row.get()[0];
    long projectId = row.get()[1];
    // 交接人已是负责人则跳过插入；否则升为 / 写入负责人
    boolean toIsOwner =
        tasks.listAssignees(taskId).stream()
            .anyMatch(a -> a[0] == toUserId && a[1] == 1);
    if (!toIsOwner) {
      tasks.insertAssignee(
          IdGenerator.nextId(), taskId, parentTaskId, projectId, toUserId, 1);
    }
  }
}
