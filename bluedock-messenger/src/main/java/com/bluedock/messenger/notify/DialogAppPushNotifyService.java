package com.bluedock.messenger.notify;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySendPublisher;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.web.dto.DialogMessageView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 会话新消息 → Kafka {@code bluedock.notify.send} channel=push；免打扰过滤（@提及除外）。
 */
@Service
public class DialogAppPushNotifyService {
  private static final Logger log = LoggerFactory.getLogger(DialogAppPushNotifyService.class);
  private static final int PREVIEW_MAX = 80;

  private final DialogRepository dialogs;
  private final UserAccountRepository users;
  private final ObjectProvider<NotifySendPublisher> notifyPublisher;

  public DialogAppPushNotifyService(
      DialogRepository dialogs,
      UserAccountRepository users,
      ObjectProvider<NotifySendPublisher> notifyPublisher) {
    this.dialogs = dialogs;
    this.users = users;
    this.notifyPublisher = notifyPublisher;
  }

  /**
   * 在 fanout 同路径调用：仅处理 {@code dialog.message}；弱提醒类型 / 静默标记跳过。
   */
  public void afterDialogMessageFanout(String type, List<Long> memberIds, Map<String, Object> data) {
    if (!RealtimeEventTypes.DIALOG_MESSAGE.equals(type) || data == null) {
      return;
    }
    if (isEventSilent(data)) {
      return;
    }
    DialogMessageView message = messageOf(data.get("message"));
    if (message == null || message.id() <= 0) {
      return;
    }
    if (isWeakType(message.type())) {
      return;
    }
    long dialogId = message.dialogId();
    if (dialogId <= 0) {
      Object raw = data.get("dialogId");
      if (raw instanceof Number n) {
        dialogId = n.longValue();
      }
    }
    if (dialogId <= 0) {
      return;
    }
    Dialog dialog = dialogs.findActive(dialogId).orElse(null);
    if (dialog == null) {
      return;
    }

    Set<Long> mentioned = mentionSet(data.get("mentionUserIds"));
    Map<Long, Boolean> mutes = dialogs.listMemberMutes(dialogId);
    long senderId = message.userId();
    String senderNick = nickOf(senderId);
    String title = titleOf(dialog, senderNick);
    String preview = previewOf(message.type(), message.body());
    String body = bodyOf(dialog, senderNick, preview);

    List<Long> targets = new ArrayList<>();
    Collection<Long> members = memberIds == null ? dialogs.listMemberUserIds(dialogId) : memberIds;
    for (Long userId : members) {
      if (userId == null || userId <= 0 || userId == senderId) {
        continue;
      }
      Optional<UserAccount> account = users.findByUserId(userId);
      if (account.isEmpty()) {
        continue;
      }
      UserAccount u = account.get();
      if (u.getIsBot() == 1 || u.getDisableAt() != null) {
        continue;
      }
      boolean muted = Boolean.TRUE.equals(mutes.get(userId));
      if (muted && !mentioned.contains(userId)) {
        continue;
      }
      targets.add(userId);
    }
    if (targets.isEmpty()) {
      return;
    }

    long finalDialogId = dialogId;
    NotifySendPublisher pub = notifyPublisher.getIfAvailable();
    if (pub == null) {
      log.debug("appPush skip: no NotifySendPublisher");
      return;
    }
    for (Long userId : targets) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("dialogId", finalDialogId);
      payload.put("messageId", message.id());
      payload.put("badge", dialogs.sumUnreadForUser(userId));
      if (mentioned.contains(userId)) {
        payload.put("mentioned", true);
      }
      pub.publish(
          new NotifySendEvent(
              IdGenerator.nextId() + "",
              NotifySendEvent.CHANNEL_PUSH,
              List.of(userId),
              title,
              body,
              payload));
    }
  }

  static boolean isEventSilent(Map<String, Object> data) {
    Object v = data.get("isSilent");
    if (v == null) {
      v = data.get("silence");
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v != null) {
      String s = String.valueOf(v).trim();
      return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }
    return false;
  }

  static boolean isWeakType(String type) {
    if (type == null || type.isBlank()) {
      return false;
    }
    return switch (type.trim().toLowerCase()) {
      case "notice", "template" -> true;
      default -> false;
    };
  }

  static String previewOf(String type, String body) {
    String t = type == null ? "text" : type.trim().toLowerCase();
    return switch (t) {
      case "image" -> "[图片]";
      case "file" -> "[文件]";
      case "audio", "voice", "record" -> "[语音]";
      case "location" -> "[位置]";
      case "video" -> "[视频]";
      case "task" -> "[任务]";
      case "meeting" -> "[会议]";
      case "vote" -> "[投票]";
      case "wordchain" -> "[接龙]";
      default -> truncate(body == null ? "" : body.trim());
    };
  }

  private static String titleOf(Dialog dialog, String senderNick) {
    if (dialog != null && "group".equals(dialog.getType())) {
      String name = dialog.getName() == null ? "" : dialog.getName().trim();
      return name.isEmpty() ? "群聊" : name;
    }
    return senderNick.isEmpty() ? "新消息" : senderNick;
  }

  private static String bodyOf(Dialog dialog, String senderNick, String preview) {
    if (dialog != null && "group".equals(dialog.getType())) {
      String nick = senderNick.isEmpty() ? "成员" : senderNick;
      return nick + ": " + preview;
    }
    return preview;
  }

  private String nickOf(long userId) {
    if (userId <= 0) {
      return "";
    }
    return users
        .findByUserId(userId)
        .map(UserAccount::getNickname)
        .map(n -> n == null ? "" : n.trim())
        .orElse("");
  }

  private static DialogMessageView messageOf(Object raw) {
    if (raw instanceof DialogMessageView view) {
      return view;
    }
    if (raw instanceof Map<?, ?> map) {
      try {
        long id = toLong(map.get("id"));
        long dialogId = toLong(map.get("dialogId"));
        long userId = toLong(map.get("userId"));
        String type = map.get("type") == null ? "text" : String.valueOf(map.get("type"));
        String body = map.get("body") == null ? "" : String.valueOf(map.get("body"));
        long replyId = toLong(map.get("replyId"));
        long tagUserId = toLong(map.get("tagUserId"));
        return new DialogMessageView(id, dialogId, userId, type, body, replyId, tagUserId, null);
      } catch (Exception e) {
        return null;
      }
    }
    return null;
  }

  private static Set<Long> mentionSet(Object raw) {
    Set<Long> out = new HashSet<>();
    if (!(raw instanceof Collection<?> col)) {
      return out;
    }
    for (Object o : col) {
      long id = toLong(o);
      if (id > 0) {
        out.add(id);
      }
    }
    return out;
  }

  private static long toLong(Object o) {
    if (o instanceof Number n) {
      return n.longValue();
    }
    if (o != null) {
      try {
        return Long.parseLong(String.valueOf(o).trim());
      } catch (NumberFormatException ignored) {
        return 0L;
      }
    }
    return 0L;
  }

  private static String truncate(String s) {
    if (s.length() <= PREVIEW_MAX) {
      return s;
    }
    return s.substring(0, PREVIEW_MAX);
  }
}
