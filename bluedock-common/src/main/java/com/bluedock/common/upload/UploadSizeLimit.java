package com.bluedock.common.upload;

/** 业务上传大小上限（如 fileSetting.uploadMaxMb）；无实现时回落 yaml。 */
public interface UploadSizeLimit {
  long maxBytesOrDefault(long fallback);
}
