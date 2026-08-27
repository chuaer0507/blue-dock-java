package com.bluedock.messenger.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.EmailSettingService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class UnreadEmailNoticeServiceTest {
  @Mock EmailSettingService emailSettings;
  @Mock SettingRepository settings;
  @Mock UnreadEmailNoticeRepository repo;
  @Mock ObjectProvider<NotifySendPublisher> notifyPublisher;
  @Mock NotifySendPublisher publisher;

  UnreadEmailNoticeService service;

  @BeforeEach
  void setUp() {
    service =
        new UnreadEmailNoticeService(
            emailSettings, settings, repo, notifyPublisher, new ObjectMapper());
  }

  @Test
  void runOnce_skipsWhenNoticeClosed() {
    when(emailSettings.loadRaw()).thenReturn(Map.of("noticeMessage", "close"));
    service.runOnce();
    verify(repo, never()).listCandidateUserIds(anyString(), any(), any(), anyInt());
  }

  @Test
  void runOnce_publishesDigestInRange() {
    Map<String, Object> cfg = new LinkedHashMap<>();
    cfg.put("noticeMessage", "open");
    cfg.put("smtpHost", "smtp.example.com");
    cfg.put("smtpUsername", "u");
    cfg.put("smtpPassword", "p");
    cfg.put("messageUnreadUserMinute", 0);
    cfg.put("messageUnreadGroupMinute", -1);
    cfg.put("messageUnreadTimeRanges", List.of(List.of("00:00", "23:59")));
    when(emailSettings.loadRaw()).thenReturn(cfg);
    when(settings.findSettingJson(NotifySettingNames.EMAIL_LAST_NOTICE))
        .thenReturn(Optional.empty());
    when(repo.listCandidateUserIds(eq("user"), any(), any(), anyInt())).thenReturn(List.of(7L));
    when(repo.nicknameOf(7L)).thenReturn("Alice");
    when(repo.listUnreadForUser(eq(7L), eq("user"), anyInt()))
        .thenReturn(
            List.of(
                new UnreadEmailNoticeRepository.UnreadRow(
                    11L,
                    100L,
                    "",
                    "user",
                    200L,
                    "text",
                    "hello",
                    3L,
                    "Bob",
                    LocalDateTime.now().minusHours(1))));
    when(notifyPublisher.getIfAvailable()).thenReturn(publisher);

    service.runOnce();

    ArgumentCaptor<NotifySendEvent> cap = ArgumentCaptor.forClass(NotifySendEvent.class);
    verify(publisher).publish(cap.capture());
    NotifySendEvent ev = cap.getValue();
    assertEquals(NotifySendEvent.CHANNEL_EMAIL, ev.channel());
    assertEquals(List.of(7L), ev.userIds());
    assertEquals("unreadDigest", ev.data().get("kind"));
    assertTrue(ev.title().contains("未读"));
    assertTrue(ev.body().contains("Alice"));
    verify(settings).upsert(eq(NotifySettingNames.EMAIL_LAST_NOTICE), anyString());
  }

  @Test
  void runOnce_skipsOutsideTimeRange() {
    Map<String, Object> cfg = new LinkedHashMap<>();
    cfg.put("noticeMessage", "open");
    cfg.put("smtpHost", "smtp.example.com");
    cfg.put("smtpUsername", "u");
    cfg.put("smtpPassword", "p");
    // 空时段 → 永不发送
    cfg.put("messageUnreadTimeRanges", List.of());
    when(emailSettings.loadRaw()).thenReturn(cfg);
    service.runOnce();
    verify(repo, never()).listCandidateUserIds(anyString(), any(), any(), anyInt());
  }
}
