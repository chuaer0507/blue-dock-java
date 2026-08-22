package com.bluedock.common.user;

/**
 * 用户离职交接：将业务归属从离职用户迁到交接人。
 *
 * <p>由 project / task / org 等模块分别实现；{@code bluedock-user} 在 {@code disable} 时按序调用。
 */
public interface UserDisableHandoverBridge {

  /**
   * 迁移归属。
   *
   * @param fromUserId 离职用户
   * @param toUserId 交接人（须已存在、非机器人、非离职）
   */
  void handover(long fromUserId, long toUserId);
}
