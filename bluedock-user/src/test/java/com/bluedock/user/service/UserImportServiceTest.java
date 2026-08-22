package com.bluedock.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.crypto.WirePasswordResolver;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.license.LicenseCapacity;
import com.bluedock.system.service.AdminGuard;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserImportServiceTest {
  @Mock UserAccountRepository users;
  @Mock AdminGuard adminGuard;
  @Mock WirePasswordResolver passwords;
  @Mock PasswordEncoder passwordEncoder;
  @Mock ObjectProvider<LicenseCapacity> licenseCapacity;
  @Mock LicenseCapacity capacity;

  UserImportService service;

  @BeforeEach
  void setUp() {
    doNothing().when(adminGuard).requireAdmin();
    when(licenseCapacity.getIfAvailable()).thenReturn(capacity);
    doNothing().when(capacity).assertCanAddUser();
    when(passwordEncoder.encode(anyString())).thenReturn("hash");
    service = new UserImportService(users, adminGuard, passwords, passwordEncoder, licenseCapacity);
  }

  @Test
  void preview_okWithoutPasswordInResponse() {
    when(users.existsByEmail(anyString())).thenReturn(false);
    String csv =
        "email,nickname,password,profession\n"
            + "new@bluedock.local,新人,Pass1234,工程师\n";
    MockMultipartFile file =
        new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    Map<String, Object> out = service.preview(file);
    assertEquals(1L, out.get("okCount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
    assertTrue((Boolean) rows.get(0).get("ok"));
    assertFalse(rows.get(0).containsKey("password"));
    assertFalse(rows.get(0).containsKey("_plain"));
  }

  @Test
  void import_emptyRows() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.importUsers(List.of(), "k1"));
    assertEquals(I18nKeys.USER_IMPORT_EMPTY, ex.getMessageKey());
  }

  @Test
  void import_createsUser() {
    when(users.existsByEmail("new@bluedock.local")).thenReturn(false);
    when(passwords.requirePlain("k1", "cipher")).thenReturn("Pass1234");

    Map<String, Object> out =
        service.importUsers(
            List.of(
                Map.of(
                    "email", "new@bluedock.local",
                    "nickname", "新人",
                    "password", "cipher",
                    "profession", "工程师")),
            "k1");
    assertEquals(1, out.get("created"));
    assertEquals(0, out.get("failed"));
    verify(users).insert(any());
  }

  @Test
  void import_skipsTakenEmail() {
    when(users.existsByEmail("a@b.com")).thenReturn(true);
    when(passwords.requirePlain("k1", "cipher")).thenReturn("Pass1234");

    Map<String, Object> out =
        service.importUsers(
            List.of(
                Map.of(
                    "email", "a@b.com",
                    "nickname", "新人",
                    "password", "cipher")),
            "k1");
    assertEquals(0, out.get("created"));
    assertEquals(1, out.get("failed"));
    verify(users, never()).insert(any());
  }

  @Test
  void preview_xlsxOk() throws Exception {
    when(users.existsByEmail(anyString())).thenReturn(false);
    byte[] bytes;
    try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var out = new java.io.ByteArrayOutputStream()) {
      var sheet = wb.createSheet();
      var header = sheet.createRow(0);
      header.createCell(0).setCellValue("email");
      header.createCell(1).setCellValue("nickname");
      header.createCell(2).setCellValue("password");
      header.createCell(3).setCellValue("profession");
      var row = sheet.createRow(1);
      row.createCell(0).setCellValue("xlsx@bluedock.local");
      row.createCell(1).setCellValue("表格用户");
      row.createCell(2).setCellValue("Pass1234");
      row.createCell(3).setCellValue("工程师");
      wb.write(out);
      bytes = out.toByteArray();
    }
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "users.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            bytes);

    Map<String, Object> out = service.preview(file);
    assertEquals(1L, out.get("okCount"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("rows");
    assertEquals("xlsx@bluedock.local", rows.get(0).get("email"));
    assertFalse(rows.get(0).containsKey("password"));
  }

  @Test
  void preview_badExtensionRejected() {
    MockMultipartFile file =
        new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1});
    BusinessException ex = assertThrows(BusinessException.class, () -> service.preview(file));
    assertEquals(I18nKeys.USER_IMPORT_FORMAT, ex.getMessageKey());
  }
}
