package com.bluedock.common.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class OssStorageFactoryTest {

  @TempDir Path tempDir;

  @Test
  void create_local_withoutPublicBase_usesFallback() {
    OssProperties props = new OssProperties();
    props.setProvider("local");
    props.getLocal().setStoragePath(tempDir.toString());

    ObjectStorage storage = OssStorageFactory.create(props, "http://fallback:8081");

    assertThat(storage.providerId()).isEqualTo("local");
    assertThat(storage.isLocal()).isTrue();
    assertThat(props.getPublicBaseUrl()).isEqualTo("http://fallback:8081");
  }

  @Test
  void create_aliyun_requiresPublicBaseAndFields() {
    OssProperties props = new OssProperties();
    props.setProvider("aliyun");
    props.getAliyun().setEndpoint("oss-cn-hangzhou.aliyuncs.com");
    props.getAliyun().setAccessKeyId("ak");
    props.getAliyun().setAccessKeySecret("sk");
    props.getAliyun().setBucket("b");

    assertThatThrownBy(() -> OssStorageFactory.create(props, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("public-base-url");

    props.setPublicBaseUrl("https://cdn.example.com");
    ObjectStorage storage = OssStorageFactory.create(props, "");
    assertThat(storage.providerId()).isEqualTo("aliyun");
  }

  @Test
  void validate_huawei_requiresEndpoint() {
    OssProperties props = new OssProperties();
    props.setProvider("huawei");
    props.setPublicBaseUrl("https://cdn.example.com");
    props.getHuawei().setAccessKey("ak");
    props.getHuawei().setSecretKey("sk");
    props.getHuawei().setBucket("b");

    assertThatThrownBy(() -> OssStorageFactory.validate(OssProvider.HUAWEI, props))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("huawei.endpoint");
  }
}
