package com.bluedock.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.system.upload.UploadObject;
import com.bluedock.system.upload.UploadObjectRepository;
import com.bluedock.system.upload.UploadObjectView;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UploadObjectServiceTest {
  @Mock ObjectStorage objectStorage;
  @Mock OssSettingService oss;
  @Mock FileSettingService fileSetting;
  @Mock UploadObjectRepository objects;
  @Mock AdminGuard adminGuard;
  @Mock SettingWriteGuard writeGuard;

  UploadObjectService service;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    service =
        new UploadObjectService(objectStorage, oss, fileSetting, objects, adminGuard, writeGuard);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void imageUpload_putsAndInserts() throws Exception {
    when(fileSetting.uploadMaxBytes()).thenReturn(10_000_000L);
    when(oss.currentProviderId()).thenReturn("local");
    when(objectStorage.put(anyString(), any(), anyLong(), anyString()))
        .thenReturn("http://cdn/media/x.png");

    MockMultipartFile file =
        new MockMultipartFile("file", "a.png", "image/png", new ByteArrayInputStream(new byte[] {1, 2}));
    Map<String, Object> out = service.imageUpload(file);

    assertEquals("http://cdn/media/x.png", out.get("url"));
    assertEquals("media", out.get("category"));
    ArgumentCaptor<UploadObject> cap = ArgumentCaptor.forClass(UploadObject.class);
    verify(objects).insert(cap.capture());
    assertEquals("media", cap.getValue().getCategory());
    assertEquals(1L, cap.getValue().getUploaderId());
  }

  @Test
  void list_requiresAdmin() {
    when(objects.count(null, null)).thenReturn(0L);
    when(objects.page(null, null, 0, 20)).thenReturn(List.of());
    Map<String, Object> out = service.list(null, null, 1, 20);
    verify(adminGuard).requireAdmin();
    assertEquals(0L, out.get("total"));
  }

  @Test
  void delete_softDeletesAndRemovesObject() {
    UploadObject row = new UploadObject();
    row.setId(9L);
    row.setObjectKey("files/x.txt");
    when(objects.findActive(9L)).thenReturn(Optional.of(row));

    Map<String, Object> out = service.delete(9L);
    assertEquals(true, out.get("ok"));
    verify(adminGuard).requireAdmin();
    verify(writeGuard).requireWritable();
    verify(objects).softDelete(9L);
    verify(objectStorage).delete("files/x.txt");
  }

  @Test
  void delete_missing_throws() {
    when(objects.findActive(9L)).thenReturn(Optional.empty());
    assertThrows(BusinessException.class, () -> service.delete(9L));
    verify(objectStorage, never()).delete(anyString());
  }

  @Test
  void normalizeCategory_defaultFiles() {
    assertEquals("files", UploadObjectService.normalizeCategory(null));
    assertEquals("media", UploadObjectService.normalizeCategory("MEDIA"));
  }

  @Test
  void imageView_listsOwnMediaAsDirsFilesShape() {
    UploadObject row = new UploadObject();
    row.setId(42L);
    row.setObjectKey("media/202608/42.png");
    row.setUrl("http://cdn/media/202608/42.png");
    row.setOriginalName("photo.png");
    row.setCreatedAt(LocalDateTime.of(2026, 8, 4, 12, 0));
    when(objects.pageByUploader(1L, "media", null, 0, 200)).thenReturn(List.of(row));

    Map<String, Object> out = service.imageView(null);
    assertEquals(List.of(), out.get("dirs"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> files = (List<Map<String, Object>>) out.get("files");
    assertEquals(1, files.size());
    assertEquals("file", files.get(0).get("type"));
    assertEquals("photo.png", files.get(0).get("title"));
    assertEquals("media/202608/42.png", files.get(0).get("path"));
    assertEquals(42L, files.get(0).get("id"));
    verify(adminGuard, never()).requireAdmin();
  }

  @Test
  void imageView_pathFiltersPrefix() {
    when(objects.pageByUploader(1L, "media", "media/202608", 0, 200)).thenReturn(List.of());
    Map<String, Object> out = service.imageView("media/202608/");
    assertEquals(List.of(), out.get("files"));
    verify(objects).pageByUploader(1L, "media", "media/202608", 0, 200);
  }

  @Test
  void sanitizeImageViewPath_stripsTraversal() {
    assertEquals("media/202608", UploadObjectService.sanitizeImageViewPath("||media/../202608||"));
    assertEquals("", UploadObjectService.sanitizeImageViewPath(null));
  }

  @Test
  void adminUpload_returnsView() throws Exception {
    when(fileSetting.uploadMaxBytes()).thenReturn(10_000_000L);
    when(oss.currentProviderId()).thenReturn("aliyun");
    when(objectStorage.put(anyString(), any(), anyLong(), anyString())).thenReturn("https://x/f");

    UploadObjectView view =
        service.adminUpload(
            new MockMultipartFile("file", "doc.pdf", "application/pdf", "hi".getBytes()), "files");
    verify(adminGuard).requireAdmin();
    verify(writeGuard).requireWritable();
    assertEquals("files", view.category());
    assertEquals("aliyun", view.provider());
    verify(objects).insert(any(UploadObject.class));
  }
}
