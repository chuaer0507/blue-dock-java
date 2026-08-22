package com.bluedock.file.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.storage.ChunkStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UploadTempCleanupSchedulerTest {
  @Mock ChunkStorage chunks;
  @Mock StringRedisTemplate redis;

  @TempDir Path temp;

  @Test
  void removesWhenRedisGone() throws Exception {
    UploadProperties props = new UploadProperties();
    props.setBaseDir(temp.toString());
    Path session = temp.resolve("tmp/chunks/7/up-1");
    Files.createDirectories(session);
    Files.writeString(session.resolve("0.part"), "x");

    when(redis.hasKey(RedisKeys.upload("up-1"))).thenReturn(false);

    new UploadTempCleanupScheduler(props, chunks, redis).tick();

    verify(chunks).deleteSession(eq(7L), eq("up-1"));
  }
}
