package com.bluedock.system.upload;

import java.time.LocalDateTime;

/** 上传库列表项（camelCase wire）。 */
public record UploadObjectView(
    long id,
    String objectKey,
    String url,
    String category,
    String originalName,
    String contentType,
    long sizeBytes,
    String provider,
    Long uploaderId,
    LocalDateTime createdAt) {

  public static UploadObjectView from(UploadObject o) {
    return new UploadObjectView(
        o.getId(),
        nullToEmpty(o.getObjectKey()),
        nullToEmpty(o.getUrl()),
        nullToEmpty(o.getCategory()),
        nullToEmpty(o.getOriginalName()),
        nullToEmpty(o.getContentType()),
        o.getSizeBytes(),
        nullToEmpty(o.getProvider()),
        o.getUploaderId(),
        o.getCreatedAt());
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }
}
