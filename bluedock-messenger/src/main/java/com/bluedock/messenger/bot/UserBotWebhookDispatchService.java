package com.bluedock.messenger.bot;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.service.TokenService;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.bot.UserBotWebhookPublisher;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.domain.DialogMessage;
import com.bluedock.messenger.mention.DialogMentionParser;
import com.bluedock.messenger.repo.DialogRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserBotWebhookDispatchService {
  private static final Logger log = LoggerFactory.getLogger(UserBotWebhookDispatchService.class);

  private final DialogRepository dialogs;
  private final UserAccountRepository users;
  private final JdbcTemplate jdbc;
  private final TokenService tokens;
  private final ObjectProvider<UserBotWebhookPublisher> publisher;
  private final ObjectMapper json;
  private final StringRedisTemplate redis;

  public UserBotWebhookDispatchService(
      DialogRepository dialogs,
      UserAccountRepository users,
      JdbcTemplate jdbc,
      TokenService tokens,
      ObjectProvider<UserBotWebhookPublisher> publisher,
      ObjectMapper json,
      StringRedisTemplate redis) {
    this.dialogs = dialogs;
    this.users = users;
    this.jdbc = jdbc;
    this.tokens = tokens;
    this.publisher = publisher;
    this.json = json;
    this.redis = redis;
  }

  public void afterTextMessage(Dialog dialog, DialogMessage msg, String text) {
    if (dialog == null || msg == null) {
      return;
    }
    Optional<UserAccount> sender = users.findByUserId(msg.getUserId());
    if (sender.isPresent() && sender.get().getIsBot() == 1) {
      return;
    }
    String body = text == null ? "" : text.trim();
    if (body.startsWith("/")) {
      return;
    }

    List<Long> members = dialogs.listMemberUserIds(dialog.getId());
    List<Long> mentions = parseMentions(body);
    String replyText = loadReplyText(msg.getReplyId());

    List<UserBotWebhookEvent> events = new ArrayList<>();
    for (Long userId : members) {
      if (userId == null || userId == msg.getUserId()) {
        continue;
      }
      Optional<UserAccount> botUser = users.findByUserId(userId);
      if (botUser.isEmpty() || botUser.get().getIsBot() != 1) {
        continue;
      }
      Optional<WebhookTarget> target = findWebhook(userId, UserBotWebhookEvent.EVENT_MESSAGE);
      if (target.isEmpty()) {
        continue;
      }
      int mention = mentions.contains(userId) || mentions.contains(0L) ? 1 : 0;
      events.add(buildMessageEvent(dialog, msg, body, replyText, botUser.get(), target.get(), mention));
    }
    publishEvents(events);
  }

  /** 群成员加入 / 离开；向会话内已配置对应事件的用户机器人投递。 */
  public void afterMemberChange(Dialog dialog, String event, long memberId, long operatorId) {
    if (dialog == null
        || (!UserBotWebhookEvent.EVENT_MEMBER_JOIN.equals(event)
            && !UserBotWebhookEvent.EVENT_MEMBER_LEAVE.equals(event)
            && !UserBotWebhookEvent.EVENT_DIALOG_OPEN.equals(event))) {
      return;
    }
    List<Long> members = dialogs.listMemberUserIds(dialog.getId());
    Map<String, Object> member = userBrief(memberId);
    Map<String, Object> operator =
        operatorId == memberId ? member : userBrief(operatorId);
    List<UserBotWebhookEvent> events = new ArrayList<>();
    for (Long userId : members) {
      if (userId == null) {
        continue;
      }
      Optional<UserAccount> botUser = users.findByUserId(userId);
      if (botUser.isEmpty() || botUser.get().getIsBot() != 1) {
        continue;
      }
      Optional<WebhookTarget> target = findWebhook(userId, event);
      if (target.isEmpty()) {
        continue;
      }
      events.add(buildMemberEvent(dialog, event, botUser.get(), target.get(), member, operator));
    }
    publishEvents(events);
  }

  /** 打开会话触发 dialogOpen（每会话每用户约 1 分钟节流）。 */
  public void afterDialogOpen(Dialog dialog, long userId) {
    if (dialog == null || userId <= 0) {
      return;
    }
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(
                RedisKeys.userBotDialogOpen(dialog.getId(), userId), "1", Duration.ofMinutes(1));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    afterMemberChange(dialog, UserBotWebhookEvent.EVENT_DIALOG_OPEN, userId, userId);
  }

  private void publishEvents(List<UserBotWebhookEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }
    UserBotWebhookPublisher pub = publisher.getIfAvailable();
    if (pub == null) {
      return;
    }
    for (UserBotWebhookEvent e : events) {
      pub.publish(e);
    }
  }

  private UserBotWebhookEvent buildMessageEvent(
      Dialog dialog,
      DialogMessage msg,
      String text,
      String replyText,
      UserAccount bot,
      WebhookTarget target,
      int mention) {
    String botToken = tokens.issue(bot.getUserId());
    Map<String, Object> messageUser = new LinkedHashMap<>();
    users
        .findByUserId(msg.getUserId())
        .ifPresent(
            u -> {
              messageUser.put("userId", u.getUserId());
              messageUser.put("email", u.getEmail());
              messageUser.put("nickname", u.getNickname());
              messageUser.put("profession", u.getProfession() == null ? "" : u.getProfession());
              messageUser.put("lang", u.getLang() == null ? "" : u.getLang());
              messageUser.put("token", tokens.issue(u.getUserId()));
            });
    long now = System.currentTimeMillis() / 1000L;
    String eventId = msg.getId() + ":" + bot.getUserId() + ":" + UserBotWebhookEvent.EVENT_MESSAGE;
    return new UserBotWebhookEvent(
        eventId,
        UserBotWebhookEvent.EVENT_MESSAGE,
        target.url(),
        bot.getUserId(),
        dialog.getId(),
        dialog.getType() == null ? "" : dialog.getType(),
        msg.getId(),
        msg.getUserId(),
        mention,
        text,
        replyText == null ? "" : replyText,
        botToken,
        messageUser,
        "{\"timestamp\":" + now + "}",
        "1.0.0",
        now,
        null,
        null,
        dialog.getGroupType() == null ? "" : dialog.getGroupType(),
        dialog.getName() == null ? "" : dialog.getName());
  }

  private UserBotWebhookEvent buildMemberEvent(
      Dialog dialog,
      String event,
      UserAccount bot,
      WebhookTarget target,
      Map<String, Object> member,
      Map<String, Object> operator) {
    long now = System.currentTimeMillis() / 1000L;
    String eventId =
        dialog.getId()
            + ":"
            + bot.getUserId()
            + ":"
            + event
            + ":"
            + (member == null ? 0 : member.getOrDefault("userId", 0))
            + ":"
            + now;
    return new UserBotWebhookEvent(
        eventId,
        event,
        target.url(),
        bot.getUserId(),
        dialog.getId(),
        dialog.getType() == null ? "" : dialog.getType(),
        0L,
        0L,
        0,
        "",
        "",
        tokens.issue(bot.getUserId()),
        Map.of(),
        "{\"timestamp\":" + now + "}",
        "1.0.0",
        now,
        member,
        operator,
        dialog.getGroupType() == null ? "" : dialog.getGroupType(),
        dialog.getName() == null ? "" : dialog.getName());
  }

  private Map<String, Object> userBrief(long userId) {
    Map<String, Object> m = new LinkedHashMap<>();
    users
        .findByUserId(userId)
        .ifPresentOrElse(
            u -> {
              m.put("userId", u.getUserId());
              m.put("nickname", u.getNickname() == null ? "" : u.getNickname());
              m.put("email", u.getEmail() == null ? "" : u.getEmail());
              m.put("isBot", u.getIsBot());
            },
            () -> {
              m.put("userId", userId);
              m.put("nickname", "");
              m.put("email", "");
              m.put("isBot", 0);
            });
    return m;
  }

  private Optional<WebhookTarget> findWebhook(long botId, String event) {
    var rows =
        jdbc.query(
            """
            SELECT webhook_url, webhook_events FROM bluedock_user_bots
            WHERE bot_id = ? LIMIT 1
            """,
            (rs, i) ->
                new WebhookTarget(
                    rs.getString("webhook_url") == null ? "" : rs.getString("webhook_url"),
                    rs.getString("webhook_events")),
            botId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    WebhookTarget t = rows.get(0);
    if (t.url().isBlank() || !t.url().matches("^https?://.+")) {
      return Optional.empty();
    }
    List<String> events = parseEvents(t.eventsJson());
    if (!events.contains(event)) {
      return Optional.empty();
    }
    return Optional.of(t);
  }

  private List<String> parseEvents(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of(UserBotWebhookEvent.EVENT_MESSAGE);
    }
    try {
      List<String> list = json.readValue(raw, new TypeReference<>() {});
      if (list == null || list.isEmpty()) {
        return List.of(UserBotWebhookEvent.EVENT_MESSAGE);
      }
      List<String> out = new ArrayList<>();
      for (String s : list) {
        if (s == null) {
          continue;
        }
        out.add(
            switch (s.trim()) {
              case "dialog_open", "dialogOpen" -> UserBotWebhookEvent.EVENT_DIALOG_OPEN;
              case "member_join", "memberJoin" -> UserBotWebhookEvent.EVENT_MEMBER_JOIN;
              case "member_leave", "memberLeave" -> UserBotWebhookEvent.EVENT_MEMBER_LEAVE;
              default -> s.trim();
            });
      }
      return out.isEmpty() ? List.of(UserBotWebhookEvent.EVENT_MESSAGE) : out;
    } catch (Exception e) {
      log.debug("webhook_events parse failed: {}", e.toString());
      return List.of(UserBotWebhookEvent.EVENT_MESSAGE);
    }
  }

  private String loadReplyText(long replyId) {
    if (replyId <= 0) {
      return "";
    }
    return dialogs
        .findMessage(replyId)
        .map(DialogMessage::getBody)
        .map(s -> s == null ? "" : s)
        .orElse("");
  }

  static List<Long> parseMentions(String text) {
    DialogMentionParser.Result r = DialogMentionParser.parse(text);
    List<Long> ids = new ArrayList<>(r.userIds());
    if (r.all()) {
      ids.add(0L);
    }
    return ids.stream().distinct().toList();
  }

  private record WebhookTarget(String url, String eventsJson) {}
}
