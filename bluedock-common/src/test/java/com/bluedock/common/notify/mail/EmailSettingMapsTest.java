package com.bluedock.common.notify.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailSettingMapsTest {
  @Test
  void toSmtp_and_ignore() {
    var cfg =
        EmailSettingMaps.toSmtp(
            Map.of(
                "smtpHost", "smtp.example.com",
                "smtpPort", "587",
                "smtpUsername", "u",
                "smtpPassword", "p",
                "smtpSsl", "close",
                "fromAlias", "BlueDock",
                "fromAddress", "noreply@example.com"));
    assertTrue(cfg.configured());
    assertEquals(587, cfg.port());
    assertEquals(
        List.of("a@x.com", "b@y.com"),
        EmailSettingMaps.parseIgnore(Map.of("ignoreAddr", "a@x.com, b@y.com")));
  }

  @Test
  void noticeAndTimeRanges() {
    assertTrue(EmailSettingMaps.noticeMessageOpen(Map.of("noticeMessage", "open")));
    assertFalse(EmailSettingMaps.noticeMessageOpen(Map.of("noticeMessage", "close")));
    assertEquals(30, EmailSettingMaps.unreadMinute(Map.of(), true));
    assertEquals(-1, EmailSettingMaps.unreadMinute(Map.of("messageUnreadGroupMinute", -1), false));

    var ranges =
        EmailSettingMaps.parseTimeRanges(
            Map.of(
                "messageUnreadTimeRanges",
                List.of(List.of("00:00", "09:00"), List.of("18:00", "23:59"))));
    assertEquals(2, ranges.size());
    assertTrue(EmailSettingMaps.isTimeInRanges(ranges, LocalTime.of(8, 30)));
    assertFalse(EmailSettingMaps.isTimeInRanges(ranges, LocalTime.of(12, 0)));
    assertFalse(EmailSettingMaps.isTimeInRanges(List.of(), LocalTime.NOON));
  }
}
