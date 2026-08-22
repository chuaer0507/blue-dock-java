package com.bluedock.file.web.dto;

import com.bluedock.common.upload.TaskAttachmentSink;

/** 分片合并结果：网盘返回 file；任务附件返回 taskFile。 */
public record UploadMergeView(String scene, Object file, Object taskFile) {
  public static UploadMergeView forCabinet(Object file) {
    return new UploadMergeView("file_cabinet", file, null);
  }

  public static UploadMergeView forTask(Object taskFile) {
    return new UploadMergeView(TaskAttachmentSink.SCENE, null, taskFile);
  }
}
