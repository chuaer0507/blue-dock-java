package com.bluedock.messenger.meeting;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.meeting.MeetingInviteBridge;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.domain.DialogMessage;
import com.bluedock.messenger.notify.DialogAppPushNotifyService;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.web.dto.DialogMessageView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessengerMeetingInviteBridge implements MeetingInviteBridge {
  private static final Logger log = LoggerFactory.getLogger(MessengerMeetingInviteBridge.class);

  private final DialogRepository dialogs;
  private final UserAccountRepository users;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final RealtimeFanoutPublisher fanout;
  private final DialogAppPushNotifyService appPushNotify;

  public MessengerMeetingInviteBridge(
      DialogRepository dialogs,
      UserAccountRepository users,
      JdbcTemplate jdbc,
      ObjectMapper json,
      RealtimeFanoutPublisher fanout,
      DialogAppPushNotifyService appPushNotify) {
    this.dialogs = dialogs;
    this.users = users;
    this.jdbc = jdbc;
    this.json = json;
    this.fanout = fanout;
    this.appPushNotify = appPushNotify;
  }

  @Override
  @Transactional
  public List<Map<String, Object>> sendInvites(
      Map<String, Object> meetingPayload, long inviterUserId, Collection<Long> inviteeUserIds) {
    List<Map<String, Object>> sent = new ArrayList<>();
    if (meetingPayload == null || inviteeUserIds == null || inviteeUserIds.isEmpty()) {
      return sent;
    }
    Optional<UserAccount> bot = users.findByEmail(MEETING_ALERT_EMAIL);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      log.warn("meeting-alert bot missing; skip invites");
      return sent;
    }
    long botUserId = bot.get().getUserId();
    String meetingId = String.valueOf(meetingPayload.getOrDefault("meetingId", ""));
    String body;
    try {
      body = json.writeValueAsString(meetingPayload);
    } catch (Exception e) {
      log.warn("meeting payload serialize failed: {}", e.toString());
      return sent;
    }

    for (Long userId : inviteeUserIds) {
      if (userId == null || userId <= 0 || userId == inviterUserId || !users.existsByUserId(userId)) {
        continue;
      }
      long dialogId = ensureUserDialog(botUserId, userId);
      LocalDateTime now = LocalDateTime.now();
      DialogMessage m = new DialogMessage();
      m.setId(IdGenerator.nextId());
      m.setDialogId(dialogId);
      m.setUserId(inviterUserId);
      m.setType("meeting");
      m.setBody(body);
      m.setReplyId(0L);
      m.setCreatedAt(now);
      dialogs.insertMessage(m);
      dialogs.touchDialog(dialogId, "[meeting]", now);
      dialogs.bumpUnreadExcept(dialogId, inviterUserId);
      jdbc.update(
          """
          INSERT INTO bluedock_meeting_messages (id, meeting_id, dialog_id, message_id)
          VALUES (?, ?, ?, ?)
          """,
          IdGenerator.nextId(),
          meetingId,
          dialogId,
          m.getId());

      DialogMessageView view = DialogMessageView.from(m);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("dialogId", dialogId);
      data.put("message", view);
      publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);

      Map<String, Object> summary = new LinkedHashMap<>();
      summary.put("id", m.getId());
      summary.put("dialogId", dialogId);
      summary.put("userId", inviterUserId);
      summary.put("type", "meeting");
      summary.put("message", meetingPayload);
      sent.add(summary);
    }
    return sent;
  }

  @Override
  @Transactional
  public void markMeetingEnded(String meetingId, Map<String, Object> meetingPayload) {
    if (meetingId == null || meetingId.isBlank() || meetingPayload == null) {
      return;
    }
    List<Map<String, Object>> rows =
        jdbc.query(
            """
            SELECT dialog_id, message_id FROM bluedock_meeting_messages WHERE meeting_id = ?
            """,
            (rs, i) -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("dialogId", rs.getLong("dialog_id"));
              m.put("messageId", rs.getLong("message_id"));
              return m;
            },
            meetingId);
    for (Map<String, Object> row : rows) {
      long dialogId = (Long) row.get("dialogId");
      long messageId = (Long) row.get("messageId");
      Optional<DialogMessage> existing = dialogs.findMessage(messageId);
      if (existing.isEmpty()) {
        continue;
      }
      String merged = mergePayload(existing.get().getBody(), meetingPayload);
      jdbc.update(
          "UPDATE bluedock_dialog_messages SET body = ?, updated_at = ? WHERE id = ?",
          merged,
          java.sql.Timestamp.valueOf(LocalDateTime.now()),
          messageId);
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("dialogId", dialogId);
      data.put("id", messageId);
      try {
        data.put("message", json.readValue(merged, Map.class));
      } catch (Exception e) {
        data.put("message", merged);
      }
      publishFanout(
          RealtimeEventTypes.DIALOG_MESSAGE_UPDATE, dialogs.listMemberUserIds(dialogId), data);
    }
  }

  private String mergePayload(String raw, Map<String, Object> patch) {
    try {
      ObjectNode node;
      if (raw == null || raw.isBlank()) {
        node = json.createObjectNode();
      } else {
        node = (ObjectNode) json.readTree(raw);
      }
      for (Map.Entry<String, Object> e : patch.entrySet()) {
        node.set(e.getKey(), json.valueToTree(e.getValue()));
      }
      return json.writeValueAsString(node);
    } catch (Exception e) {
      try {
        return json.writeValueAsString(patch);
      } catch (Exception ex) {
        return raw == null ? "{}" : raw;
      }
    }
  }

  private long ensureUserDialog(long userIdA, long userIdB) {
    Optional<Long> existing = dialogs.findUserDialogId(userIdA, userIdB);
    if (existing.isPresent()) {
      return existing.get();
    }
    LocalDateTime now = LocalDateTime.now();
    Dialog d = new Dialog();
    d.setId(IdGenerator.nextId());
    d.setType("user");
    d.setGroupType("");
    d.setName("");
    d.setAvatar("");
    d.setOwnerId(userIdA);
    d.setLinkId(0L);
    d.setLastMessage("");
    d.setLastAt(now);
    d.setCreatedAt(now);
    dialogs.insertDialog(d);
    dialogs.insertMember(IdGenerator.nextId(), d.getId(), userIdA);
    dialogs.insertMember(IdGenerator.nextId(), d.getId(), userIdB);
    return d.getId();
  }

  private void publishFanout(String type, List<Long> userIds, Map<String, Object> data) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    RealtimeFanoutEvent event =
        new RealtimeFanoutEvent(
            UUID.randomUUID().toString().replace("-", ""), type, List.copyOf(userIds), data);
    fanout.publish(event);
    appPushNotify.afterDialogMessageFanout(type, userIds, data);
  }
}
