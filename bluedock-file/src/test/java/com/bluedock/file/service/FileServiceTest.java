package com.bluedock.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.browse.BrowseRecorder;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.storage.ChunkStorage;
import com.bluedock.file.web.dto.FileView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {
  @Mock FileRepository files;
  @Mock FileAccessService access;
  @Mock FileShareService shares;
  @Mock ObjectProvider<BrowseRecorder> browseRecorder;
  @Mock ObjectProvider<ChunkStorage> chunkStorage;
  @InjectMocks FileService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void add_folder() {
    when(files.countByParent(1L, 0L)).thenReturn(0);
    FileView view = service.add(null, 0L, "Docs", "folder");
    assertEquals("Docs", view.name());
    assertEquals("folder", view.type());
    verify(files).insert(any(FileEntry.class));
  }

  @Test
  void lists_ok() {
    FileEntry f = new FileEntry();
    f.setId(9L);
    f.setName("a");
    f.setType("folder");
    when(files.listByParent(1L, 0L)).thenReturn(List.of(f));
    when(files.listSharedRoots(1L)).thenReturn(List.of());
    assertEquals(1, service.lists(0L).size());
  }

  @Test
  void one_denied() {
    when(access.requireReadable(3L, 1L)).thenThrow(new BusinessException(1500, "file.denied"));
    assertThrows(BusinessException.class, () -> service.one(3L));
  }

  @Test
  void remove_ok() {
    FileEntry f = owned(3L, 0L, "folder");
    when(access.requireOwnerOrCreator(3L, 1L)).thenReturn(f);
    when(files.findActive(3L)).thenReturn(Optional.of(f));
    when(files.listChildIds(1L, 3L)).thenReturn(List.of());
    service.remove(3L);
    verify(shares).cleanupShare(eq(3L), any());
    verify(files).softDelete(eq(3L), any());
  }

  @Test
  void trash_listsRoots() {
    FileEntry f = owned(5L, 0L, "file");
    f.setDeletedAt(java.time.LocalDateTime.now());
    when(files.listTrashRoots(1L)).thenReturn(List.of(f));
    assertEquals(1, service.trash().size());
  }

  @Test
  void restore_ok() {
    FileEntry deleted = owned(7L, 0L, "folder");
    deleted.setDeletedAt(java.time.LocalDateTime.now());
    FileEntry active = owned(7L, 0L, "folder");
    when(access.requireOwnerOrCreatorDeleted(7L, 1L)).thenReturn(deleted);
    when(files.countByParent(1L, 0L)).thenReturn(0);
    when(files.listDeletedChildIds(1L, 7L)).thenReturn(List.of(8L));
    when(files.listDeletedChildIds(1L, 8L)).thenReturn(List.of());
    when(files.findActive(7L)).thenReturn(Optional.of(active));

    FileView view = service.restore(7L);
    assertEquals(7L, view.id());
    verify(files).restoreWithParent(eq(7L), eq(0L), any());
    verify(files).clearDeleted(eq(8L), any());
  }

  @Test
  void restore_remountsWhenParentGone() {
    FileEntry deleted = owned(9L, 20L, "file");
    deleted.setDeletedAt(java.time.LocalDateTime.now());
    FileEntry active = owned(9L, 0L, "file");
    when(access.requireOwnerOrCreatorDeleted(9L, 1L)).thenReturn(deleted);
    when(files.findActive(20L)).thenReturn(Optional.empty());
    when(files.countByParent(1L, 0L)).thenReturn(0);
    when(files.listDeletedChildIds(1L, 9L)).thenReturn(List.of());
    when(files.findActive(9L)).thenReturn(Optional.of(active));

    service.restore(9L);
    verify(files).restoreWithParent(eq(9L), eq(0L), any());
  }

  @Test
  void move_rejects_into_self() {
    FileEntry f = owned(10L, 0L, "folder");
    when(access.requireOwner(10L, 1L)).thenReturn(f);
    assertThrows(BusinessException.class, () -> service.move(10L, 10L));
  }

  @Test
  void move_ok() {
    FileEntry src = owned(10L, 0L, "file");
    FileEntry dest = owned(20L, 0L, "folder");
    when(access.requireOwner(10L, 1L)).thenReturn(src);
    when(access.requireWritable(20L, 1L)).thenReturn(dest);
    when(files.findActive(20L)).thenReturn(Optional.of(dest));
    when(files.countByParent(1L, 20L)).thenReturn(0);
    FileView view = service.move(10L, 20L);
    assertEquals(20L, view.parentId());
    verify(files).move(eq(10L), eq(20L), any());
  }

  @Test
  void copy_file() {
    FileEntry src = owned(10L, 0L, "file");
    src.setName("a.txt");
    src.setHash("abc");
    src.setPath("file/x/content");
    when(access.requireReadable(10L, 1L)).thenReturn(src);
    when(files.countByParent(1L, 0L)).thenReturn(0);
    FileView view = service.copy(10L, 0L);
    assertEquals("a.txt", view.name());
    verify(files).insert(any(FileEntry.class));
  }

  @Test
  void search_requires_key() {
    assertThrows(BusinessException.class, () -> service.search("  ", 10));
  }

  @Test
  void search_ok() {
    FileEntry f = owned(9L, 0L, "file");
    f.setName("readme");
    when(files.searchByName(eq(1L), eq("read"), anyInt())).thenReturn(List.of(f));
    when(files.listSharedRoots(1L)).thenReturn(List.of());
    assertEquals(1, service.search("read", 20).size());
  }

  private static FileEntry owned(long id, long parentId, String type) {
    FileEntry f = new FileEntry();
    f.setId(id);
    f.setParentId(parentId);
    f.setUserId(1L);
    f.setCreatedUserId(1L);
    f.setType(type);
    f.setName("n" + id);
    return f;
  }
}
