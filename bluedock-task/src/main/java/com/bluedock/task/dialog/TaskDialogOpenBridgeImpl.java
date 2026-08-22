package com.bluedock.task.dialog;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.project.TaskDialogAccessBridge;
import com.bluedock.common.project.TaskDialogOpenBridge;
import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.task.domain.TaskItem;
import com.bluedock.task.repo.TaskRepository;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 校验可见后确保任务群存在；供 messenger {@code sendAiAssistant} 等使用。 */
@Component
public class TaskDialogOpenBridgeImpl implements TaskDialogOpenBridge {
  private final TaskRepository tasks;
  private final TaskDialogAccessBridge access;
  private final TaskDialogMembership membership;
  private final TaskGroupBridge groupBridge;

  public TaskDialogOpenBridgeImpl(
      TaskRepository tasks,
      TaskDialogAccessBridge access,
      TaskDialogMembership membership,
      @Autowired(required = false) TaskGroupBridge groupBridge) {
    this.tasks = tasks;
    this.access = access;
    this.membership = membership;
    this.groupBridge = groupBridge;
  }

  @Override
  @Transactional
  public long ensureAccessibleDialog(long taskId, long userId) {
    TaskItem t =
        tasks
            .findActive(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND));
    if (t.getParentId() != 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_SUBTASK_NESTED);
    }
    if (!access.canAccessTaskDialog(taskId, userId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND);
    }
    if (groupBridge == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OPEN_FAILED);
    }
    Set<Long> members = membership.resolveMembers(t);
    members.add(userId);
    long dialogId = groupBridge.ensureGroup(taskId, t.getName(), t.getUserId(), members);
    if (t.getDialogId() != dialogId) {
      tasks.updateDialogId(taskId, dialogId);
    }
    return dialogId;
  }
}
