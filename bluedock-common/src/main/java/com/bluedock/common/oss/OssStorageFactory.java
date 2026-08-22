package com.bluedock.common.oss;

import org.springframework.util.StringUtils;

/** 根据 {@link OssProperties} 构造具体 {@link ObjectStorage}。 */
public final class OssStorageFactory {

  private OssStorageFactory() {}

  public static ObjectStorage create(OssProperties properties, String releasePublicBaseFallback) {
    OssProvider provider = properties.resolvedProvider();
    validate(provider, properties);
    if (!StringUtils.hasText(properties.getPublicBaseUrl()) && provider == OssProvider.LOCAL) {
      if (StringUtils.hasText(releasePublicBaseFallback)) {
        properties.setPublicBaseUrl(releasePublicBaseFallback);
      }
    }
    if (!StringUtils.hasText(properties.getPublicBaseUrl()) && provider != OssProvider.LOCAL) {
      throw new IllegalArgumentException("task.oss.public-base-url 必填");
    }
    return switch (provider) {
      case LOCAL -> new LocalObjectStorage(properties, releasePublicBaseFallback);
      case HUAWEI -> new HuaweiObjectStorage(properties);
      case ALIYUN -> new AliyunObjectStorage(properties);
      case TENCENT -> new TencentObjectStorage(properties);
      case QINIU -> new QiniuObjectStorage(properties);
    };
  }

  public static void validate(OssProvider provider, OssProperties properties) {
    switch (provider) {
      case LOCAL -> {
        // ok
      }
      case HUAWEI -> {
        require(properties.getHuawei().getEndpoint(), "huawei.endpoint");
        require(properties.getHuawei().getAccessKey(), "huawei.accessKey");
        require(properties.getHuawei().getSecretKey(), "huawei.secretKey");
        require(properties.getHuawei().getBucket(), "huawei.bucket");
      }
      case ALIYUN -> {
        require(properties.getAliyun().getEndpoint(), "aliyun.endpoint");
        require(properties.getAliyun().getAccessKeyId(), "aliyun.accessKeyId");
        require(properties.getAliyun().getAccessKeySecret(), "aliyun.accessKeySecret");
        require(properties.getAliyun().getBucket(), "aliyun.bucket");
      }
      case TENCENT -> {
        require(properties.getTencent().getRegion(), "tencent.region");
        require(properties.getTencent().getSecretId(), "tencent.secretId");
        require(properties.getTencent().getSecretKey(), "tencent.secretKey");
        require(properties.getTencent().getBucket(), "tencent.bucket");
      }
      case QINIU -> {
        require(properties.getQiniu().getAccessKey(), "qiniu.accessKey");
        require(properties.getQiniu().getSecretKey(), "qiniu.secretKey");
        require(properties.getQiniu().getBucket(), "qiniu.bucket");
      }
    }
  }

  private static void require(String value, String name) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(name + " 必填");
    }
  }
}
