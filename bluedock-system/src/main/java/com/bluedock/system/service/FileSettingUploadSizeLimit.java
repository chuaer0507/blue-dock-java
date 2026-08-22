package com.bluedock.system.service;

import com.bluedock.common.upload.UploadSizeLimit;
import org.springframework.stereotype.Component;

@Component
public class FileSettingUploadSizeLimit implements UploadSizeLimit {
  private final FileSettingService fileSetting;

  public FileSettingUploadSizeLimit(FileSettingService fileSetting) {
    this.fileSetting = fileSetting;
  }

  @Override
  public long maxBytesOrDefault(long fallback) {
    long fromSetting = fileSetting.uploadMaxBytes();
    return Math.min(fallback, fromSetting);
  }
}
