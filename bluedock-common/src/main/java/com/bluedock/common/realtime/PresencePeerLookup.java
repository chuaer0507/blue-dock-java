package com.bluedock.common.realtime;

import java.util.List;

/**
 * 查询与某用户共享会话的对端 userId（用于 presence 扇出）；由 messenger 实现。
 */
public interface PresencePeerLookup {
  /**
   * @param userId 上/下线用户
   * @return 对端用户（不含本人、机器人、已禁用）；空表示无可通知对象
   */
  List<Long> peerUserIds(long userId);
}
