package com.bluedock.messenger.project;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.project.TaskAiDialogBridge;
import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.messenger.domain.DialogMessage;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.service.DialogService;
import com.bluedock.messenger.web.dto.DialogMessageView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessengerTaskAiDialogBridge implements TaskAiDialogBridge {
  private static final Logger log = LoggerFactory.getLogger(MessengerTaskAiDialogBridge.class);
  private static final Pattern AI_ACTION =
      Pattern.compile(":::ai-action\\{([^}]*)\\}:::", Pattern.CASE_INSENSITIVE);

  private final TaskGroupBridge groups;
  private final DialogService dialogs;
  private final DialogRepository dialogRepo;
  private final UserAccountRepository users;

  public MessengerTaskAiDialogBridge(
      TaskGroupBridge groups,
      DialogService dialogs,
      DialogRepository dialogRepo,
      UserAccountRepository users) {
    this.groups = groups;
    this.dialogs = dialogs;
    this.dialogRepo = dialogRepo;
    this.users = users;
  }

  @Override
  @Transactional
  public long publishSuggestion(
      long taskId,
      String taskName,
      long ownerUserId,
      Collection<Long> memberIds,
      String markdown) {
    Optional<UserAccount> bot = users.findByEmail(AI_BOT_EMAIL);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      log.warn("AI bot {} missing; skip suggestion card", AI_BOT_EMAIL);
      return 0L;
    }
    long botUserId = bot.get().getUserId();
    Set<Long> members = new HashSet<>();
    if (memberIds != null) {
      members.addAll(memberIds);
    }
    if (ownerUserId > 0) {
      members.add(ownerUserId);
    }
    members.add(botUserId);
    long dialogId = groups.ensureGroup(taskId, taskName, ownerUserId, members);
    if (!dialogRepo.isMember(dialogId, botUserId)) {
      dialogRepo.insertMember(
          com.bluedock.common.util.IdGenerator.nextId(), dialogId, botUserId);
    }
    String body = markdown == null ? "" : markdown;
    try {
      DialogMessageView sent = dialogs.sendMarkdownAsBot(botUserId, dialogId, body);
      long messageId = sent.id();
      if (messageId > 0 && body.contains("message_id=0")) {
        String patched = body.replace("message_id=0", "message_id=" + messageId);
        sent = dialogs.updateMessageAsBot(botUserId, dialogId, messageId, patched);
      }
      return sent.id();
    } catch (Exception ex) {
      log.warn("publish AI suggestion failed taskId={}: {}", taskId, ex.toString());
      return 0L;
    }
  }

  @Override
  @Transactional
  public Map<String, Object> updateActionStatus(
      long dialogId, long messageId, String type, String status, long userId, long related) {
    Optional<DialogMessage> opt = dialogRepo.findMessage(messageId);
    if (opt.isEmpty()) {
      return null;
    }
    DialogMessage existing = opt.get();
    long resolvedDialog = dialogId > 0 ? dialogId : existing.getDialogId();
    if (existing.getDialogId() != resolvedDialog) {
      return null;
    }
    Optional<UserAccount> bot = users.findByEmail(AI_BOT_EMAIL);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      return null;
    }
    String body = existing.getBody() == null ? "" : existing.getBody();
    String patched = patchStatus(body, type, status, userId, related);
    if (patched.equals(body)) {
      return toMap(DialogMessageView.from(existing));
    }
    try {
      DialogMessageView view =
          dialogs.updateMessageAsBot(bot.get().getUserId(), resolvedDialog, messageId, patched);
      return toMap(view);
    } catch (Exception ex) {
      log.warn("update AI action status failed messageId={}: {}", messageId, ex.toString());
      return null;
    }
  }

  static String patchStatus(String body, String type, String status, long userId, long related) {
    if (body == null || body.isBlank() || type == null || type.isBlank()) {
      return body == null ? "" : body;
    }
    String wantType = type.trim().toLowerCase(Locale.ROOT);
    String wantStatus = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    Matcher m = AI_ACTION.matcher(body);
    StringBuffer sb = new StringBuffer();
    boolean any = false;
    while (m.find()) {
      String attrs = m.group(1) == null ? "" : m.group(1).trim();
      Map<String, String> map = parseAttrs(attrs);
      String t = attr(map, "type");
      if (t == null || !wantType.equals(t.toLowerCase(Locale.ROOT))) {
        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
        continue;
      }
      if (userId > 0) {
        long blockUser = parseLong(attr(map, "userId"));
        if (blockUser > 0 && blockUser != userId) {
          m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
          continue;
        }
      }
      if (related > 0) {
        long blockRelated = parseLong(attr(map, "related"));
        if (blockRelated > 0 && blockRelated != related) {
          m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
          continue;
        }
      }
      putAttr(map, "status", wantStatus);
      String rebuilt = ":::ai-action{" + joinAttrs(map) + "}:::";
      m.appendReplacement(sb, Matcher.quoteReplacement(rebuilt));
      any = true;
    }
    m.appendTail(sb);
    return any ? sb.toString() : body;
  }

  private static Map<String, String> parseAttrs(String attrs) {
    Map<String, String> map = new LinkedHashMap<>();
    if (attrs == null || attrs.isBlank()) {
      return map;
    }
    for (String part : attrs.split("\\s+")) {
      if (part.isBlank()) {
        continue;
      }
      int eq = part.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      // 保留 wire 键名大小写（如 userId），查找时用 attr() 忽略大小写
      map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
    }
    return map;
  }

  private static String attr(Map<String, String> map, String name) {
    if (map == null || name == null) {
      return null;
    }
    String direct = map.get(name);
    if (direct != null) {
      return direct;
    }
    for (Map.Entry<String, String> e : map.entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
        return e.getValue();
      }
    }
    return null;
  }

  private static void putAttr(Map<String, String> map, String name, String value) {
    for (String key : map.keySet()) {
      if (key != null && key.equalsIgnoreCase(name)) {
        map.put(key, value);
        return;
      }
    }
    map.put(name, value);
  }

  private static String joinAttrs(Map<String, String> map) {
    ArrayList<String> parts = new ArrayList<>();
    for (Map.Entry<String, String> e : map.entrySet()) {
      if (e.getKey() == null || e.getKey().isBlank()) {
        continue;
      }
      parts.add(e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()));
    }
    return String.join(" ", parts);
  }

  private static long parseLong(String s) {
    if (s == null || s.isBlank()) {
      return 0L;
    }
    try {
      return Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static Map<String, Object> toMap(DialogMessageView v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", v.id());
    m.put("dialogId", v.dialogId());
    m.put("userId", v.userId());
    m.put("type", v.type());
    m.put("message", v.body());
    m.put("replyId", v.replyId());
    m.put("createdAt", v.createdAt());
    return m;
  }
}
