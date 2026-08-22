package com.bluedock.common.oss;

/** 上传后缀校验；由 system 模块按当前 OSS allowExtensions 实现。 */
public interface OssExtensionChecker {
  void assertAllowed(String originalFilename);
}
