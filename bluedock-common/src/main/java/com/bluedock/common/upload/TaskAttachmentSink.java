package com.bluedock.common.upload;

import java.util.Map;

/** 上传 merge 后按场景写入任务附件；由 bluedock-task 实现。 */
public interface TaskAttachmentSink {
  String SCENE = "project_task";

  Map<String, Object> save(
      long taskId, String name, long size, String extension, String path, String thumbnail);
}
