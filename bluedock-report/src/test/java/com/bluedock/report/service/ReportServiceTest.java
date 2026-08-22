package com.bluedock.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.report.ReportDialogBridge;
import com.bluedock.report.domain.Report;
import com.bluedock.report.repo.ReportRepository;
import com.bluedock.report.web.dto.ReportView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {
  @Mock ReportRepository reports;
  @Mock UserAccountRepository users;
  @Mock ReportDialogBridge dialogBridge;

  ReportService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
    service = new ReportService(reports, users, dialogBridge, null);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void store_ok() {
    when(users.existsByUserId(2L)).thenReturn(true);
    when(reports.existsSign(eq(1L), eq("daily"), any())).thenReturn(false);

    ReportView view = service.store(null, "Daily", "daily", "done", "2", 0);
    assertEquals("Daily", view.title());
    verify(reports).insert(any(Report.class));
    verify(reports).insertReceive(anyLong(), anyLong(), eq(2L), any());
  }

  @Test
  void store_no_receive() {
    assertThrows(
        BusinessException.class, () -> service.store(null, "Daily", "daily", "done", "1", 0));
  }

  @Test
  void detail_denied() {
    Report r = new Report();
    r.setId(9L);
    r.setUserId(3L);
    when(reports.findActive(9L)).thenReturn(Optional.of(r));
    when(reports.isReceiver(9L, 1L)).thenReturn(false);
    assertThrows(BusinessException.class, () -> service.detail(9L, null));
  }

  @Test
  void share_ok() {
    Report r = new Report();
    r.setId(9L);
    r.setUserId(1L);
    r.setTitle("日报");
    when(reports.findActive(9L)).thenReturn(Optional.of(r));
    when(reports.findLinkByReportIdAndUserId(9L, 1L)).thenReturn(Optional.empty());
    when(dialogBridge.sendText(eq(7L), anyString())).thenReturn(100L);

    Map<String, Object> out = service.share("9", "7", null);
    assertEquals(1, out.get("sharedCount"));
    verify(reports).insertLink(anyLong(), eq(9L), eq(1L), anyString(), any());
  }

  @Test
  void share_requiresDialog() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.share("9", "", null));
    assertEquals(I18nKeys.REPORT_DIALOG_REQUIRED, ex.getMessageKey());
  }

  @Test
  void analysisSave_ok() {
    Report r = new Report();
    r.setId(9L);
    r.setUserId(1L);
    when(reports.findActive(9L)).thenReturn(Optional.of(r));

    Map<String, Object> out =
        service.analysisSave(Map.of("id", 9, "text", "要点总结", "model", "gpt"));
    assertEquals("要点总结", out.get("text"));
    verify(reports).upsertAnalysis(anyLong(), eq(9L), eq(1L), eq("要点总结"), eq("gpt"), eq(null), any());
  }

  @Test
  void aiGenerate_requiresContent() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.aiGenerate("daily", "  "));
    assertEquals(I18nKeys.REPORT_CONTENT_EMPTY, ex.getMessageKey());
  }

  @Test
  void aiGenerate_requiresBridge() {
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.aiGenerate("daily", "今日完成了 A"));
    assertEquals(I18nKeys.REPORT_AI_UNAVAILABLE, ex.getMessageKey());
  }

  @Test
  void template_daily_aggregatesTasks() {
    UserAccount u = new UserAccount();
    u.setNickname("张三");
    u.setEmail("a@b.c");
    when(users.findByUserId(1L)).thenReturn(Optional.of(u));
    when(reports.listOwnerTaskNames(eq(1L), any(), any(), eq(true))).thenReturn(List.of("完成 A"));
    when(reports.listOwnerTaskNames(eq(1L), any(), any(), eq(false)))
        .thenReturn(List.of("未完成 B"));

    Map<String, Object> out = service.template("daily", 0);
    assertEquals("daily", out.get("type"));
    assertTrue(String.valueOf(out.get("title")).contains("张三的日报"));
    String content = String.valueOf(out.get("content"));
    assertTrue(content.contains("已完成工作"));
    assertTrue(content.contains("完成 A"));
    assertTrue(content.contains("今日未完成"));
    assertTrue(content.contains("未完成 B"));
    assertEquals(1, out.get("completedCount"));
  }

  @Test
  void detail_byCode() {
    when(reports.findLinkByCode("abc")).thenReturn(Optional.of(Map.of("reportId", 9L, "code", "abc")));
    Report r = new Report();
    r.setId(9L);
    r.setUserId(2L);
    r.setTitle("t");
    when(reports.findActive(9L)).thenReturn(Optional.of(r));
    when(reports.listReceiveUserIds(9L)).thenReturn(java.util.List.of());
    when(reports.findAnalysis(9L, 1L)).thenReturn(Optional.empty());

    ReportView view = service.detail(null, "abc");
    assertNotNull(view);
    assertEquals(9L, view.id());
    verify(reports).incrementLinkOpenCount("abc");
  }
}
