package com.bluedock.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.upload.TaskAttachmentSink;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.UploadInitView;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {
  @Mock StringRedisTemplate redis;
  @Mock HashOperations<String, Object, Object> hashOps;
  @Mock SetOperations<String, String> setOps;
  @Mock FileRepository files;
  @Mock ChunkStorage storage;
  @Mock ObjectProvider<TaskAttachmentSink> taskAttachments;
  @Mock ObjectProvider<com.bluedock.common.oss.OssExtensionChecker> extensionChecker;
  @Mock ObjectProvider<com.bluedock.common.upload.UploadSizeLimit> sizeLimit;

  UploadProperties props = new UploadProperties();
  UploadService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    service =
        new UploadService(
            redis, files, storage, props, taskAttachments, extensionChecker, sizeLimit);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void init_instant() {
    FileEntry f = new FileEntry();
    f.setId(11L);
    f.setName("a.png");
    f.setHash("0123456789abcdef0123456789abcdef");
    when(files.findByUserAndHash(1L, "0123456789abcdef0123456789abcdef"))
        .thenReturn(Optional.of(f));

    UploadInitView view =
        service.init("0123456789abcdef0123456789abcdef", 100, "a.png", "file_cabinet", 0L, null);
    assertTrue(view.done());
    assertEquals(11L, view.file().id());
  }

  @Test
  void init_creates_session() {
    when(files.findByUserAndHash(1L, "0123456789abcdef0123456789abcdef"))
        .thenReturn(Optional.empty());
    when(redis.opsForHash()).thenReturn(hashOps);

    UploadInitView view =
        service.init(
            "0123456789abcdef0123456789abcdef", 10_000_000, "big.bin", "file_cabinet", null, null);
    assertTrue(!view.done());
    assertEquals(2, view.chunkCount());
    verify(hashOps).putAll(anyString(), anyMap());
  }

  @Test
  void init_project_task_requires_task_id() {
    assertThrows(
        BusinessException.class,
        () ->
            service.init(
                "0123456789abcdef0123456789abcdef",
                100,
                "a.png",
                TaskAttachmentSink.SCENE,
                null,
                null));
  }

  @Test
  void init_project_task_skips_cabinet_instant() {
    when(redis.opsForHash()).thenReturn(hashOps);

    UploadInitView view =
        service.init(
            "0123456789abcdef0123456789abcdef",
            100,
            "a.png",
            TaskAttachmentSink.SCENE,
            null,
            99L);
    assertTrue(!view.done());
    verify(hashOps).putAll(anyString(), anyMap());
  }

  @Test
  void cancel_silent_when_missing() {
    when(redis.opsForHash()).thenReturn(hashOps);
    when(hashOps.entries(RedisKeys.upload("missing"))).thenReturn(Map.of());
    service.cancel("missing");
  }

  @Test
  void cancel_owner() {
    when(redis.opsForHash()).thenReturn(hashOps);
    when(hashOps.entries(RedisKeys.upload("u1"))).thenReturn(Map.of("userId", "1"));
    service.cancel("u1");
    verify(storage).deleteSession(1L, "u1");
    verify(redis).delete(eq(RedisKeys.upload("u1")));
    verify(redis).delete(eq(RedisKeys.uploadChunks("u1")));
  }
}
