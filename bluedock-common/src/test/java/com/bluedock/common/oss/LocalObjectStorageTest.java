package com.bluedock.common.oss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bluedock.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageTest {

  @TempDir Path tempDir;

  @Test
  void putAndDelete_roundTrip() throws Exception {
    OssProperties props = new OssProperties();
    props.setProvider("local");
    props.setPublicBaseUrl("http://localhost:8081");
    props.getLocal().setStoragePath(tempDir.toString());

    LocalObjectStorage storage = new LocalObjectStorage(props, "");
    byte[] body = "hello-oss".getBytes(StandardCharsets.UTF_8);
    String url =
        storage.put(
            "files/smoke.txt", new ByteArrayInputStream(body), body.length, "text/plain");

    assertThat(url).isEqualTo("http://localhost:8081/files/smoke.txt");
    Path written = tempDir.resolve("files/smoke.txt");
    assertThat(Files.readString(written)).isEqualTo("hello-oss");

    storage.delete("files/smoke.txt");
    assertThat(Files.exists(written)).isFalse();
  }

  @Test
  void blankStoragePath_defaultsToPublicDir() {
    OssProperties props = new OssProperties();
    props.setProvider("local");
    props.setPublicBaseUrl("http://localhost:8081");
    props.getLocal().setStoragePath("");

    LocalObjectStorage storage = new LocalObjectStorage(props, "");
    assertThat(storage.resolveRoot().getFileName().toString()).isEqualTo("uploads");
  }

  @Test
  void put_rejectsPathTraversal() {
    OssProperties props = new OssProperties();
    props.setProvider("local");
    props.setPublicBaseUrl("http://localhost:8081");
    props.getLocal().setStoragePath(tempDir.toString());
    LocalObjectStorage storage = new LocalObjectStorage(props, "");

    assertThatThrownBy(
            () ->
                storage.put(
                    "../escape.txt",
                    new ByteArrayInputStream(new byte[] {1}),
                    1,
                    "application/octet-stream"))
        .isInstanceOf(BusinessException.class);
  }
}
