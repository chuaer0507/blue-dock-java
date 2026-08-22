package com.bluedock.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.file.domain.FileContent;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileContentRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.web.dto.FileContentView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileContentServiceTest {
  @Mock FileRepository files;
  @Mock FileContentRepository contents;
  @Mock FileAccessService access;
  @Mock UploadService uploads;
  @InjectMocks FileContentService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void save_ok() {
    FileEntry doc = doc(9L);
    when(access.requireWritable(9L, 1L)).thenReturn(doc);
    FileContentView view = service.save(9L, "hello");
    assertEquals("hello", view.content());
    verify(contents).insert(any(FileContent.class));
    verify(files).updateSize(eq(9L), eq(5L), any());
  }

  @Test
  void save_rejects_folder() {
    FileEntry folder = new FileEntry();
    folder.setId(1L);
    folder.setUserId(1L);
    folder.setType("folder");
    when(access.requireWritable(1L, 1L)).thenReturn(folder);
    assertThrows(BusinessException.class, () -> service.save(1L, "x"));
  }

  @Test
  void restore_copies_version() {
    FileEntry doc = doc(9L);
    FileContent old = new FileContent();
    old.setId(100L);
    old.setFileId(9L);
    old.setContent("old body");
    when(access.requireWritable(9L, 1L)).thenReturn(doc);
    when(contents.findActive(100L)).thenReturn(Optional.of(old));
    FileContentView view = service.restore(9L, 100L);
    assertEquals("old body", view.content());
    verify(contents).insert(any(FileContent.class));
  }

  @Test
  void history_ok() {
    FileEntry doc = doc(9L);
    FileContent c = new FileContent();
    c.setId(1L);
    c.setSize(3);
    c.setUserId(1L);
    when(access.requireReadable(9L, 1L)).thenReturn(doc);
    when(contents.listHistory(9L, 50)).thenReturn(List.of(c));
    assertEquals(1, service.history(9L, null).size());
  }

  private static FileEntry doc(long id) {
    FileEntry f = new FileEntry();
    f.setId(id);
    f.setUserId(1L);
    f.setType("document");
    return f;
  }
}
