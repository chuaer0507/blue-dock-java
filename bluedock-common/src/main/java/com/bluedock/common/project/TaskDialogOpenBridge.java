package com.bluedock.common.project;

/**
 * 校验可见后打开/复用任务群；由 bluedock-task 实现，messenger 可选注入。
 *
 * <p>供 {@code sendAiAssistant} 等在仅有 {@code taskId} 时解析 {@code dialogId}。
 */
public interface TaskDialogOpenBridge {
  /**
   * @return 任务群 dialogId
   * @throws com.bluedock.common.exception.BusinessException 任务不存在、不可见或无权限
   */
  long ensureAccessibleDialog(long taskId, long userId);
}
