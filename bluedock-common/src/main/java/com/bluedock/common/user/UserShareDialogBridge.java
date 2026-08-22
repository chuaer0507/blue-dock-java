package com.bluedock.common.user;

import java.util.List;
import java.util.Map;

/** 分享选择器：会话候选；由 bluedock-messenger 实现。 */
public interface UserShareDialogBridge {
  /** 最近会话（无关键词时）。 */
  List<Map<String, Object>> listRecent(long userId, int take);

  /** 按关键词搜会话。 */
  List<Map<String, Object>> search(long userId, String key, int take);

  /** 打开/复用与对方的单聊；失败返回 0。 */
  long ensureUserDialog(long me, long peerUserId);
}
