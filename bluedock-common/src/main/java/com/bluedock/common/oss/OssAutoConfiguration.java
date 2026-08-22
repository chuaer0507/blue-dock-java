package com.bluedock.common.oss;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssAutoConfiguration {

  @Bean
  RuntimeObjectStorage runtimeObjectStorage(OssProperties properties) {
    // 启动默认走 yaml；云厂商缺凭证时回退 local，便于 Admin 后续配置
    OssProperties boot = properties;
    String publicBase =
        StringUtils.hasText(boot.getPublicBaseUrl()) ? boot.getPublicBaseUrl() : "";
    try {
      if (boot.resolvedProvider() != OssProvider.LOCAL) {
        OssStorageFactory.validate(boot.resolvedProvider(), boot);
      }
    } catch (IllegalArgumentException ex) {
      boot = copyAsLocal(properties, publicBase);
    }
    if (!StringUtils.hasText(boot.getPublicBaseUrl())) {
      boot.setPublicBaseUrl(publicBase);
    }
    ObjectStorage initial = OssStorageFactory.create(boot, publicBase);
    return new RuntimeObjectStorage(initial);
  }

  @Bean
  ObjectStorage objectStorage(RuntimeObjectStorage runtimeObjectStorage) {
    return runtimeObjectStorage;
  }

  private static OssProperties copyAsLocal(OssProperties source, String publicBase) {
    OssProperties local = new OssProperties();
    local.setProvider("local");
    local.setPublicBaseUrl(
        StringUtils.hasText(source.getPublicBaseUrl()) ? source.getPublicBaseUrl() : publicBase);
    local.getLocal().setStoragePath(source.getLocal().getStoragePath());
    return local;
  }
}
