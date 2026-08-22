package com.bluedock.common.upload;

import java.io.InputStream;

/** 会话直传文件落盘 + 写 bluedock_files；由 bluedock-file 实现，messenger 可选注入。 */
public interface DialogChatFileSink {
  record Saved(long fileId, String name, String type, String extension, long size, String path) {}

  Saved save(long userId, long dialogId, String filename, long size, InputStream content);
}
