package com.bluedock.common.user;

import java.util.List;
import java.util.Map;

/** 分享选择器：文件目录；由 bluedock-file 实现。 */
public interface UserShareFileBridge {
  /**
   * 列出用户可见的子文件夹。
   *
   * @return 每项含 {@code id}/{@code name}/{@code isShared}
   */
  List<Map<String, Object>> listFolders(long userId, long parentId);
}
