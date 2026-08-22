package com.bluedock.common.oss;

/** 对象存储提供方。 */
public enum OssProvider {
  LOCAL,
  HUAWEI,
  ALIYUN,
  TENCENT,
  QINIU;

  public static OssProvider fromConfig(String raw) {
    if (raw == null || raw.isBlank()) {
      return LOCAL;
    }
    return OssProvider.valueOf(raw.trim().toUpperCase());
  }
}
