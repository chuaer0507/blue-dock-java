package com.bluedock.system.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 系统图片 / 通用文件直传；委托 {@link UploadObjectService} 落盘并登记 {@code bluedock_upload_objects}。 */
@Service
public class SystemMediaUploadService {
  private final UploadObjectService uploads;

  public SystemMediaUploadService(UploadObjectService uploads) {
    this.uploads = uploads;
  }

  public Map<String, Object> imageUpload(MultipartFile file) {
    return uploads.imageUpload(file);
  }

  public Map<String, Object> fileUpload(MultipartFile file) {
    return uploads.fileUpload(file);
  }
}
