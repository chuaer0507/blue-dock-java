package com.bluedock.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.domain.FileLink;
import com.bluedock.file.domain.FileUser;
import com.bluedock.file.repo.FileLinkRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.repo.FileUserRepository;
import com.bluedock.file.web.dto.FileLinkView;
import com.bluedock.file.web.dto.FileShareView;
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
class FileShareServiceTest {
  @Mock FileRepository files;
  @Mock FileUserRepository fileUsers;
  @Mock FileLinkRepository fileLinks;
  @Mock FileAccessService access;
  @InjectMocks FileShareService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void update_adds_member() {
    FileEntry file = owned(9L);
    when(access.requireOwner(9L, 1L)).thenReturn(file);
    when(fileUsers.countByFileId(9L)).thenReturn(0, 1);
    when(fileUsers.findActive(9L, 2L)).thenReturn(Optional.empty());
    when(fileUsers.listByFileId(9L)).thenReturn(List.of(member(2L)));
    when(fileLinks.findActiveByFileId(9L)).thenReturn(Optional.empty());
    FileShareView view = service.update(9L, "2", null, 1);
    assertEquals(1, view.isShared());
    assertEquals(1, view.members().size());
    verify(fileUsers).insert(any(FileUser.class));
    verify(files).updateShare(eq(9L), eq(1), any());
  }

  @Test
  void update_rejects_nested() {
    FileEntry child = owned(9L);
    child.setParentId(8L);
    FileEntry parent = owned(8L);
    parent.setIsShared(1);
    when(access.requireOwner(9L, 1L)).thenReturn(child);
    when(files.findActive(8L)).thenReturn(Optional.of(parent));
    assertThrows(BusinessException.class, () -> service.update(9L, "2", null, 0));
  }

  @Test
  void link_creates() {
    FileEntry file = owned(9L);
    when(access.requireOwner(9L, 1L)).thenReturn(file);
    when(fileLinks.findActiveByFileId(9L)).thenReturn(Optional.empty());
    FileLinkView view = service.link(9L, null, 0, 0);
    assertNotNull(view.code());
    verify(fileLinks).insert(any(FileLink.class));
  }

  @Test
  void share_out_ok() {
    FileEntry file = owned(9L);
    file.setUserId(99L);
    when(access.requireReadable(9L, 1L)).thenReturn(file);
    when(fileUsers.findActive(9L, 1L)).thenReturn(Optional.of(member(1L)));
    service.shareOut(9L);
    verify(fileUsers).hardDelete(9L, 1L);
  }

  private static FileEntry owned(long id) {
    FileEntry f = new FileEntry();
    f.setId(id);
    f.setParentId(0L);
    f.setUserId(1L);
    f.setIsShared(0);
    f.setType("folder");
    return f;
  }

  private static FileUser member(long userId) {
    FileUser u = new FileUser();
    u.setId(1L);
    u.setFileId(9L);
    u.setUserId(userId);
    u.setPermission(1);
    return u;
  }
}
