package com.bluedock.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.oss.LocalObjectStorage;
import com.bluedock.common.oss.OssProperties;
import com.bluedock.file.config.UploadProperties;
import com.bluedock.file.domain.FileContent;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileContentRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.FilePackView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;
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
class FilePackServiceTest {
  @Mock FileRepository files;
  @Mock FileContentRepository contents;
  @Mock FileAccessService access;
  @Mock StringRedisTemplate redis;
  @Mock ValueOperations<String, String> values;
  @TempDir Path temp;

  UploadProperties props = new UploadProperties();
  ChunkStorage storage;
  FilePackService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    props.setBaseDir(temp.toString());
    OssProperties oss = new OssProperties();
    oss.getLocal().setStoragePath(temp.toString());
    storage = new ChunkStorage(props, new LocalObjectStorage(oss, ""));
    when(redis.opsForValue()).thenReturn(values);
    service = new FilePackService(files, contents, access, storage, props, redis);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void pack_document() throws Exception {
    FileEntry doc = new FileEntry();
    doc.setId(9L);
    doc.setUserId(1L);
    doc.setType("document");
    doc.setName("note");
    doc.setPath("");
    when(access.requireReadable(9L, 1L)).thenReturn(doc);
    FileContent c = new FileContent();
    c.setContent("hello pack");
    when(contents.findLatest(9L)).thenReturn(Optional.of(c));

    FilePackView view = service.pack("9");
    assertEquals("note.zip", view.name());
    assertTrue(view.size() > 0);
    Path zip = storage.resolveRelative(view.path());
    assertTrue(Files.isRegularFile(zip));
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      assertEquals(1, zf.size());
    }
    verify(values).set(anyString(), anyString(), eq(java.time.Duration.ofHours(2)));
  }

  @Test
  void pack_folder_with_child() throws Exception {
    FileEntry folder = new FileEntry();
    folder.setId(1L);
    folder.setUserId(1L);
    folder.setType("folder");
    folder.setName("Docs");
    FileEntry child = new FileEntry();
    child.setId(2L);
    child.setUserId(1L);
    child.setType("document");
    child.setName("a.md");
    child.setPath("");
    when(access.requireReadable(1L, 1L)).thenReturn(folder);
    when(files.listByParent(1L, 1L)).thenReturn(List.of(child));
    FileContent c = new FileContent();
    c.setContent("x");
    when(contents.findLatest(2L)).thenReturn(Optional.of(c));

    FilePackView view = service.pack("1");
    Path zip = storage.resolveRelative(view.path());
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      assertTrue(zf.size() >= 2);
    }
  }
}
