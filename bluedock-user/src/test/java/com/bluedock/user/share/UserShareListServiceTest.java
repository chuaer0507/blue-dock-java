package com.bluedock.user.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.user.UserShareDialogBridge;
import com.bluedock.common.user.UserShareFileBridge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserShareListServiceTest {
  @Mock ObjectProvider<UserShareFileBridge> filesProvider;
  @Mock ObjectProvider<UserShareDialogBridge> dialogsProvider;
  @Mock UserShareFileBridge files;
  @Mock UserShareDialogBridge dialogs;
  @Mock UserAccountRepository users;

  UserShareListService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    when(filesProvider.getIfAvailable()).thenReturn(files);
    when(dialogsProvider.getIfAvailable()).thenReturn(dialogs);
    service = new UserShareListService(filesProvider, dialogsProvider, users);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void list_rootIncludesFilesAndDialogs() {
    when(dialogs.listRecent(eq(1L), anyInt()))
        .thenReturn(List.of(Map.of("id", 9L, "name", "D", "avatar", "", "sort", 10L)));

    List<Map<String, Object>> rows = service.list("file", null, null);
    assertTrue(rows.size() >= 2);
    assertEquals("children", rows.get(0).get("type"));
    assertEquals("item", rows.get(1).get("type"));
    assertEquals(9L, ((Map<?, ?>) rows.get(1).get("extend")).get("dialogIds"));
  }

  @Test
  void list_parentDrillsFolders() {
    when(files.listFolders(1L, 3L))
        .thenReturn(List.of(Map.of("id", 5L, "name", "Sub", "isShared", false)));

    List<Map<String, Object>> rows = service.list("file", null, 3L);
    assertEquals(1, rows.size());
    assertEquals("children", rows.get(0).get("type"));
    assertTrue(String.valueOf(rows.get(0).get("url")).contains("parentId=5"));
  }

  @Test
  void list_textUsesSendTextUrl() {
    when(dialogs.listRecent(anyLong(), anyInt()))
        .thenReturn(List.of(Map.of("id", 2L, "name", "A", "avatar", "", "sort", 1L)));
    List<Map<String, Object>> rows = service.list("text", null, null);
    assertEquals(1, rows.size());
    assertEquals("/api/dialog/message/sendText", rows.get(0).get("url"));
  }
}
