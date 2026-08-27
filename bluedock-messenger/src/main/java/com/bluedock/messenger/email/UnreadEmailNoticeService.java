package com.bluedock.messenger.email;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.common.notify.mail.EmailSettingMaps;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.system.repo.SettingRepository;
import com.bluedock.system.service.EmailSettingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 未读消息邮件汇总。
 *
 * <p>
 * 免打扰会话写入 {@code silence=1} 的读回执，本任务跳过；投递走 Kafka
 * {@code bluedock.notify.send}，Worker
 * 成功发信后置 {@code email=1}。
 */
@Service
public class UnreadEmailNoticeService {
  private static final Logger log = LoggerFactory.getLogger(UnreadEmailNoticeService.class);
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final int CHUNK = 100;

  private final EmailSettingService emailSettings;
  private final SettingRepository settings;
  private final UnreadEmailNoticeRepository repo;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;
  private final ObjectMapper objectMapper;

  public UnreadEmailNoticeService(
      EmailSettingService emailSettings,
      SettingRepository settings,
      UnreadEmailNoticeRepository repo,
      ObjectProvider<NotifySendPublisher> notifyPublisher,
      ObjectMapper objectMapper) {
    this.emailSettings = emailSettings;
    this.settings = settings;
    this.repo = repo;
    this.notifyPublisher = notifyPublisher;
    this.objectMapper = objectMapper;
  }

  public void runOnce() {
    Map<String, Object> cfg = emailSettings.loadRaw();
    if (!EmailSettingMaps.noticeMessageOpen(cfg)) {
      return;
    }
    if (!EmailSettingMaps.toSmtp(cfg).configured()) {
      log.debug("unread email notice skip: smtp not configured");
      return;
    }
    if (!EmailSettingMaps.isTimeInRanges(cfg)) {
      return;
    }
    processDialogType(cfg, true);
    processDialogType(cfg, false);
  }

  private void processDialogType(Map<String, Object> cfg, boolean userDialog) {
    int minute = EmailSettingMaps.unreadMinute(cfg, userDialog);
    if (minute <= -1) {
      return;
    }
    String dialogType = userDialog ? "user" : "group";
    String lastKey = userDialog ? "timeUser" : "timeGroup";
    Map<String, Object> cursor = loadCursor();
    LocalDateTime start = parseCursor(cursor.get(lastKey));
    LocalDateTime end = LocalDateTime.now().minusMinutes(minute);
    if (start.isAfter(end)) {
      return;
    }

    List<Long> users = repo.listCandidateUserIds(dialogType, start, end, CHUNK);
    for (Long userId : users) {
      try {
        sendForUser(userId, dialogType, userDialog);
      } catch (Exception e) {
        log.warn("unread email notice fail userId={}: {}", userId, e.toString());
      }
    }
    cursor.put(lastKey, end.format(TS));
    saveCursor(cursor);
  }

  private void sendForUser(long userId, String dialogType, boolean userDialog) {
    List<UnreadEmailNoticeRepository.UnreadRow> rows = repo.listUnreadForUser(userId, dialogType, CHUNK);
    if (rows.isEmpty()) {
      return;
    }
    NotifySendPublisher pub = notifyPublisher.getIfAvailable();
    if (pub == null) {
      log.debug("unread email notice skip: no NotifySendPublisher");
      return;
    }
    String nickname = repo.nicknameOf(userId);
    if (nickname.isEmpty()) {
      nickname = "User";
    }
    Map<Long, List<UnreadEmailNoticeRepository.UnreadRow>> byDialog = new LinkedHashMap<>();
    for (UnreadEmailNoticeRepository.UnreadRow row : rows) {
      byDialog.computeIfAbsent(row.dialogId(), k -> new ArrayList<>()).add(row);
    }
    String kindLabel = userDialog ? "单聊" : "群聊";
    String subject;
    if (byDialog.size() > 1) {
      subject = String.format(Locale.ROOT, "来自%d个%s未读消息提醒", byDialog.size(), kindLabel);
    } else {
      UnreadEmailNoticeRepository.UnreadRow first = rows.get(0);
      String name = dialogDisplayName(first, userDialog);
      subject = String.format(Locale.ROOT, "来自%s未读消息提醒", name);
    }
    StringBuilder body = new StringBuilder();
    body.append(nickname)
        .append("，您好。\n您有")
        .append(byDialog.size())
        .append("条未读")
        .append(kindLabel)
        .append("消息，请及时处理。\n\n");
    List<Long> readIds = new ArrayList<>(rows.size());
    for (Map.Entry<Long, List<UnreadEmailNoticeRepository.UnreadRow>> e : byDialog.entrySet()) {
      List<UnreadEmailNoticeRepository.UnreadRow> items = e.getValue();
      String name = dialogDisplayName(items.get(0), userDialog);
      body.append("【")
          .append(name)
          .append("】")
          .append(items.size())
          .append("条未读\n");
      for (UnreadEmailNoticeRepository.UnreadRow item : items) {
        body.append("- ")
            .append(item.senderName().isBlank() ? ("#" + item.senderId()) : item.senderName())
            .append(": ")
            .append(preview(item))
            .append('\n');
        readIds.add(item.readId());
      }
      body.append('\n');
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("kind", "unreadDigest");
    data.put("dialogType", dialogType);
    data.put("messageReadIds", readIds);
    String eventId = "email-unread-" + IdGenerator.nextId();
    pub.publish(
        new NotifySendEvent(
            eventId,
            NotifySendEvent.CHANNEL_EMAIL,
            List.of(userId),
            subject,
            body.toString(),
            data));
  }

  private static String dialogDisplayName(
      UnreadEmailNoticeRepository.UnreadRow row, boolean userDialog) {
    if (userDialog) {
      if (!row.senderName().isBlank()) {
        return row.senderName();
      }
      return "私聊";
    }
    if (!row.dialogName().isBlank()) {
      return row.dialogName();
    }
    return "群聊#" + row.dialogId();
  }

  private static String preview(UnreadEmailNoticeRepository.UnreadRow row) {
    String type = row.messageType() == null ? "" : row.messageType();
    return switch (type) {
      case "file" -> "[文件]";
      case "record" -> "[语音]";
      case "meeting" -> "[会议]";
      default -> {
        String raw = row.body() == null ? "" : row.body().replaceAll("\\s+", " ").trim();
        if (raw.length() > 120) {
          raw = raw.substring(0, 120) + "…";
        }
        yield raw.isEmpty() ? "[消息]" : raw;
      }
    };
  }

  private Map<String, Object> loadCursor() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("timeUser", LocalDate.now().atStartOfDay().format(TS));
    out.put("timeGroup", LocalDate.now().atStartOfDay().format(TS));
    settings
        .findSettingJson(NotifySettingNames.EMAIL_LAST_NOTICE)
        .ifPresent(
            json -> {
              try {
                out.putAll(
                    objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
                    }));
              } catch (Exception ignored) {
                // keep defaults
              }
            });
    return out;
  }

  private void saveCursor(Map<String, Object> cursor) {
    try {
      settings.upsert(NotifySettingNames.EMAIL_LAST_NOTICE, objectMapper.writeValueAsString(cursor));
    } catch (Exception e) {
      log.warn("save emailLastNotice failed: {}", e.toString());
    }
  }

  private static LocalDateTime parseCursor(Object raw) {
    if (raw == null) {
      return LocalDate.now().atStartOfDay();
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return LocalDate.now().atStartOfDay();
    }
    try {
      return LocalDateTime.parse(s, TS);
    } catch (Exception e) {
      try {
        return LocalDateTime.parse(s);
      } catch (Exception e2) {
        return LocalDate.now().atStartOfDay();
      }
    }
  }
}
