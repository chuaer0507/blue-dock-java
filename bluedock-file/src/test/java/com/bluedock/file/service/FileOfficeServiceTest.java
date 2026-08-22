package com.bluedock.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.oss.LocalObjectStorage;
import com.bluedock.common.oss.OssProperties;
import com.bluedock.file.config.OfficeProperties;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileContentRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.OfficeTokenView;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class FileOfficeServiceTest {
  @Mock
  FileRepository files;
  @Mock
  FileContentRepository contents;
  @Mock
  FileAccessService access;
  @Mock
  StringRedisTemplate redis;
  @Mock
  ValueOperations<String, String> values;
  @TempDir
  Path temp;

  OfficeProperties officeProps = new OfficeProperties();
  UploadProperties uploadProps = new UploadProperties();
  FileOfficeService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    officeProps.setAllowDevToken(true);
    officeProps.setEnabled(false);
    officeProps.setPublicBaseUrl("http://localhost:18080");
    officeProps.setJwtSecret("test-secret-at-least-32-chars!!");
    uploadProps.setBaseDir(temp.toString());
    when(redis.opsForValue()).thenReturn(values);
    OssProperties oss = new OssProperties();
    oss.getLocal().setStoragePath(temp.toString());
    service = new FileOfficeService(
        files,
        contents,
        access,
        new ChunkStorage(uploadProps, new LocalObjectStorage(oss, "")),
        uploadProps,
        officeProps,
        redis);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void token_ok() {
    FileEntry f = new FileEntry();
    f.setId(9L);
    f.setUserId(1L);
    f.setType("word");
    f.setName("a.docx");
    f.setExtension("docx");
    f.setUpdatedAt(LocalDateTime.now());
    when(access.requireWritable(9L, 1L)).thenReturn(f);

    OfficeTokenView view = service.token(9L, "edit");
    assertEquals("edit", view.mode());
    assertEquals("docx", view.fileType());
    assertFalse(view.token().isBlank());
    assertFalse(view.jwt().isBlank());
    verify(values).set(anyString(), anyString(), any(Duration.class));
  }
}
