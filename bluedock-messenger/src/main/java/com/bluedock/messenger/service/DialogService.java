package com.bluedock.messenger.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.bot.UserBotWebhookEvent;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.realtime.RealtimeEventTypes;
import com.bluedock.common.realtime.RealtimeFanoutEvent;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.search.SearchIndexEvent;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.common.oss.ObjectStorage;
import com.bluedock.common.project.TaskCardBridge;
import com.bluedock.common.project.TaskDialogAccessBridge;
import com.bluedock.common.project.TaskDialogOpenBridge;
import com.bluedock.common.project.TaskMentionBridge;
import com.bluedock.common.upload.DialogChatFileSink;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.bot.UserBotWebhookDispatchService;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.domain.DialogMessage;
import com.bluedock.messenger.mention.DialogMentionParser;
import com.bluedock.messenger.notify.DialogAppPushNotifyService;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.sticker.StickerSearchService;
import com.bluedock.messenger.web.dto.DialogConfigView;
import com.bluedock.messenger.web.dto.DialogMessageTranslationView;
import com.bluedock.messenger.web.dto.DialogMessageDetailView;
import com.bluedock.messenger.web.dto.DialogMessageDownload;
import com.bluedock.messenger.web.dto.DialogMessageEmojiView;
import com.bluedock.messenger.web.dto.DialogMessageReadListView;
import com.bluedock.messenger.web.dto.DialogMessageTagView;
import com.bluedock.messenger.web.dto.DialogMessageTodoView;
import com.bluedock.messenger.web.dto.DialogMessageView;
import com.bluedock.messenger.web.dto.DialogMergeDetailView;
import com.bluedock.messenger.web.dto.DialogSessionView;
import com.bluedock.messenger.web.dto.DialogTelephoneView;
import com.bluedock.messenger.web.dto.DialogUnreadItemView;
import com.bluedock.messenger.web.dto.DialogView;
import com.bluedock.system.ai.AiBotChatService;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.system.service.SystemGeneralSettingService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DialogService {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_WORDCHAIN_CHARS = 200_000;
  private static final int MAX_NOTICE_CHARS = 500;
  private static final int MAX_BOT_TEXT_CHARS = 2000;
  private static final int MAX_TEMPLATE_ITEM_CHARS = 300;
  private static final int MAX_TEMPLATE_TITLE_CHARS = 50;
  private static final int MIN_RECORD_MS = 600;
  private static final int MAX_RECORD_BYTES = 10 * 1024 * 1024;
  private static final int MAX_AI_ASSISTANT_CHARS = 200_000;
  private static final int MAX_AI_NICKNAME_CHARS = 20;
  private static final Set<String> LOCATION_TYPES = Set.of("baidu", "amap", "tencent");
  private static final Set<String> APPROVE_CARD_TYPES =
      Set.of(
          "approve_reviewer",
          "approve_notifier",
          "approve_submitter",
          "approve_comment_notifier");
  private static final Set<String> SYSTEM_BOT_PREFIXES =
      Set.of(
          "system-msg",
          "task-alert",
          "todo-alert",
          "attendance",
          "anon-msg",
          "approval-alert",
          "meeting-alert",
          "okr-alert",
          "bot-manager");

  private final DialogRepository dialogs;
  private final UserAccountRepository users;
  private final RealtimeFanoutPublisher fanout;
  private final SearchIndexPublisher searchIndex;
  private final UserBotWebhookDispatchService userBotWebhook;
  private final ObjectProvider<DialogChatFileSink> chatFiles;
  private final ObjectProvider<TaskMentionBridge> taskMentions;
  private final ObjectProvider<TaskDialogAccessBridge> taskDialogAccess;
  private final ObjectProvider<TaskDialogOpenBridge> taskDialogOpen;
  private final ObjectProvider<TaskCardBridge> taskCards;
  private final ObjectProvider<SystemGeneralSettingService> systemSettings;
  private final ObjectProvider<PasswordEncoder> passwordEncoder;
  private final ObjectProvider<ObjectStorage> objectStorage;
  private final ObjectProvider<AiBotChatService> aiBotChat;
  private final AdminGuard adminGuard;
  private final DialogAppPushNotifyService appPushNotify;
  private final StickerSearchService stickerSearch;
  private final ObjectProvider<com.bluedock.messenger.session.DialogSessionTitleService> sessionTitles;

  public DialogService(
      DialogRepository dialogs,
      UserAccountRepository users,
      RealtimeFanoutPublisher fanout,
      SearchIndexPublisher searchIndex,
      UserBotWebhookDispatchService userBotWebhook,
      ObjectProvider<DialogChatFileSink> chatFiles,
      ObjectProvider<TaskMentionBridge> taskMentions,
      ObjectProvider<TaskDialogAccessBridge> taskDialogAccess,
      ObjectProvider<TaskDialogOpenBridge> taskDialogOpen,
      ObjectProvider<TaskCardBridge> taskCards,
      ObjectProvider<SystemGeneralSettingService> systemSettings,
      ObjectProvider<PasswordEncoder> passwordEncoderProvider,
      ObjectProvider<ObjectStorage> objectStorage,
      ObjectProvider<AiBotChatService> aiBotChat,
      AdminGuard adminGuard,
      DialogAppPushNotifyService appPushNotify,
      StickerSearchService stickerSearch,
      ObjectProvider<com.bluedock.messenger.session.DialogSessionTitleService> sessionTitles) {
    this.dialogs = dialogs;
    this.users = users;
    this.fanout = fanout;
    this.searchIndex = searchIndex;
    this.userBotWebhook = userBotWebhook;
    this.chatFiles = chatFiles;
    this.taskMentions = taskMentions;
    this.taskDialogAccess = taskDialogAccess;
    this.taskDialogOpen = taskDialogOpen;
    this.taskCards = taskCards;
    this.systemSettings = systemSettings;
    this.passwordEncoder = passwordEncoderProvider;
    this.objectStorage = objectStorage;
    this.aiBotChat = aiBotChat;
    this.adminGuard = adminGuard;
    this.appPushNotify = appPushNotify;
    this.stickerSearch = stickerSearch;
    this.sessionTitles = sessionTitles;
  }

  public List<DialogView> lists() {
    long userId = AuthContext.requireUserId();
    return dialogs.listForUser(userId).stream()
        .filter(d -> allowTaskDialog(d, userId))
        .map(DialogView::from)
        .toList();
  }

  /** 列表外会话：当前用户已隐藏的会话。 */
  public List<DialogView> beyond() {
    long userId = AuthContext.requireUserId();
    return dialogs.listHiddenForUser(userId).stream()
        .filter(d -> allowTaskDialog(d, userId))
        .map(DialogView::from)
        .toList();
  }

  /** 搜索会话；{@code key} 匹配群名、最后消息、单聊对方昵称/邮箱。 */
  public List<DialogView> search(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    String q = key == null ? "" : key.trim();
    if (q.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SEARCH_KEY_REQUIRED);
    }
    if (q.length() > 64) {
      q = q.substring(0, 64);
    }
    int n = take == null ? 50 : Math.min(Math.max(take, 1), 100);
    String like = "%" + escapeLike(q) + "%";
    return dialogs.searchForUser(userId, like, n).stream()
        .filter(d -> allowTaskDialog(d, userId))
        .map(DialogView::from)
        .toList();
  }

  /** 搜索已标注会话；{@code key} 匹配个人 tag；空 key 列出全部有标签会话。 */
  public List<DialogView> searchTag(String key, Integer take) {
    long userId = AuthContext.requireUserId();
    String q = key == null ? "" : key.trim();
    if (q.length() > 64) {
      q = q.substring(0, 64);
    }
    int n = take == null ? 50 : Math.min(Math.max(take, 1), 100);
    String like = q.isEmpty() ? "%" : "%" + escapeLike(q) + "%";
    return dialogs.searchByTag(userId, like, n).stream().map(DialogView::from).toList();
  }

  public DialogView one(long dialogId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    DialogView view =
        dialogs.listForUser(userId).stream()
            .filter(x -> x.getId() == dialogId)
            .findFirst()
            .map(DialogView::from)
            .orElseGet(
                () -> {
                  Dialog d =
                      dialogs
                          .findActive(dialogId)
                          .orElseThrow(
                              () ->
                                  new BusinessException(
                                      ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
                  d.setUnreadCount(0);
                  d.setIsTop(0);
                  return DialogView.from(d);
                });
    dialogs.findActive(dialogId).ifPresent(d -> userBotWebhook.afterDialogOpen(d, userId));
    return view;
  }

  public List<Long> members(long dialogId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    return dialogs.listMemberUserIds(dialogId);
  }

  @Transactional
  public DialogView openUser(long peerUserId) {
    long me = AuthContext.requireUserId();
    if (peerUserId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_PEER_REQUIRED);
    }
    if (peerUserId == me) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OPEN_FAILED);
    }
    if (!users.existsByUserId(peerUserId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }

    Optional<Long> existing = dialogs.findUserDialogId(me, peerUserId);
    if (existing.isPresent()) {
      return one(existing.get());
    }

    LocalDateTime now = LocalDateTime.now();
    Dialog d = new Dialog();
    d.setId(IdGenerator.nextId());
    d.setType("user");
    d.setGroupType("");
    d.setName("");
    d.setAvatar("");
    d.setOwnerId(me);
    d.setLinkId(0L);
    d.setLastMessage("");
    d.setLastAt(now);
    d.setCreatedAt(now);
    d.setUnreadCount(0);
    d.setIsTop(0);
    dialogs.insertDialog(d);
    dialogs.insertMember(IdGenerator.nextId(), d.getId(), me);
    dialogs.insertMember(IdGenerator.nextId(), d.getId(), peerUserId);
    return DialogView.from(d);
  }

  public List<DialogMessageView> messageList(long dialogId, Long beforeId, Integer take) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    int n = take == null ? 50 : take;
    List<DialogMessage> messages =
        dialogs.listMessages(dialogId, beforeId, n, dialogs.findUserSessionKey(dialogId, userId));
    List<DialogMessageView> views = new ArrayList<>(messages.size());
    for (DialogMessage m : messages) {
      views.add(DialogMessageView.from(m));
    }
    Collections.reverse(views);
    return views;
  }

  @Transactional
  public DialogMessageView sendText(long dialogId, String text, Long replyId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    assertCanSpeak(dialogId, userId);
    assertNotHumanToBotDm(dialogId, userId);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    String body = text == null ? "" : text.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (body.length() > 5000) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_TOO_LONG);
    }

    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(userId);
    m.setType("text");
    m.setBody(body);
    m.setReplyId(replyId == null ? 0L : replyId);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, body, now);
    dialogs.bumpUnreadExcept(dialogId, userId);
    initMessageReads(dialogId, m.getId(), userId, now);
    List<Long> mentioned = recordUserMentions(dialogId, m.getId(), userId, body);
    if (!mentioned.isEmpty()) {
      dialogs.clearMessageReadSilent(m.getId(), mentioned);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    if (!mentioned.isEmpty()) {
      data.put("mentionUserIds", mentioned);
    }
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    publishSearchIndex(
        SearchIndexEvent.ACTION_UPSERT,
        SearchIndexEvent.TYPE_MESSAGE,
        m.getId(),
        userId,
        0L,
        body.length() > 80 ? body.substring(0, 80) : body,
        body);

    Dialog dialog = dialogs.findActive(dialogId).orElse(null);
    userBotWebhook.afterTextMessage(dialog, m, body);
    recordTaskMentions(dialogId, m.getId(), userId, body);
    var titles = sessionTitles.getIfAvailable();
    if (titles != null) {
      titles.scheduleIfNeeded(dialogId, userId, body);
    }

    return DialogMessageView.from(m);
  }

  public DialogMessageView messageOne(long messageId) {
    long userId = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), userId);
    return DialogMessageView.from(message);
  }

  /**
   * 契约 {@code GET /api/dialog/message/latest}：按会话拉取 {@code latestId} 之后的新消息。
   *
   * <p>{@code dialogs} 为 JSON 数组，项含 {@code id}/{@code dialogId} 与可选 {@code
   * latestId}；最多 5 个会话，每会话最多 {@code take}（默认 25，上限 50）条。
   */
  public List<DialogMessageView> messageLatest(String dialogsJson, Integer take) {
    long me = AuthContext.requireUserId();
    List<long[]> cursors = parseLatestDialogs(dialogsJson);
    if (cursors.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_LATEST_INVALID);
    }
    int n = take == null ? 25 : Math.min(Math.max(take, 1), 50);
    List<DialogMessageView> out = new ArrayList<>();
    int dialogCount = 0;
    for (long[] cursor : cursors) {
      if (dialogCount >= 5) {
        break;
      }
      long dialogId = cursor[0];
      long latestId = cursor[1];
      if (dialogId <= 0) {
        continue;
      }
      dialogCount++;
      requireMember(dialogId, me);
      List<DialogMessage> messages = dialogs.listMessagesAfter(dialogId, latestId, n);
      List<DialogMessageView> views = new ArrayList<>(messages.size());
      for (DialogMessage m : messages) {
        views.add(DialogMessageView.from(m));
      }
      Collections.reverse(views);
      out.addAll(views);
    }
    return out;
  }

  /**
   * 契约 {@code GET /api/dialog/message/detail}：消息详情；{@code onlyUpdateAt=yes} 仅返回 id/updatedAt。
   */
  public Object messageDetail(long messageId, String onlyUpdateAt) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    LocalDateTime updated =
        message.getUpdatedAt() != null ? message.getUpdatedAt() : message.getCreatedAt();
    if (isTruthy(onlyUpdateAt)) {
      Map<String, Object> slim = new LinkedHashMap<>();
      slim.put("id", message.getId());
      slim.put("updatedAt", updated);
      return slim;
    }
    Map<String, Object> file = null;
    String type = message.getType() == null ? "" : message.getType();
    if ("file".equals(type) || "image".equals(type)) {
      long fileId = extractFileId(message.getBody());
      if (fileId > 0) {
        file = dialogs.findFileMeta(fileId).orElse(null);
      }
    }
    return new DialogMessageDetailView(
        message.getId(),
        message.getDialogId(),
        message.getUserId(),
        type,
        message.getBody() == null ? "" : message.getBody(),
        message.getReplyId(),
        message.getCreatedAt(),
        updated,
        file);
  }

  /**
   * 契约 {@code GET /api/dialog/message/download}：附件下载；{@code down=preview} 返回 URL 元数据。
   */
  public DialogMessageDownload messageDownload(long messageId, String down) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    String type = message.getType() == null ? "" : message.getType();
    if (!"file".equals(type) && !"image".equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_DOWNLOAD_TYPE);
    }
    long fileId = extractFileId(message.getBody());
    if (fileId <= 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_FILE_INVALID);
    }
    Map<String, Object> meta =
        dialogs
            .findFileMeta(fileId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_FILE_INVALID));
    String name = String.valueOf(meta.getOrDefault("name", "file"));
    String path = String.valueOf(meta.getOrDefault("path", ""));
    long size = ((Number) meta.getOrDefault("size", 0L)).longValue();
    String url = path.isBlank() ? "" : "/" + path.replaceAll("^/+", "");
    boolean preview = "preview".equalsIgnoreCase(down == null ? "" : down.trim());
    if (preview) {
      return new DialogMessageDownload(true, fileId, name, url, size, null);
    }
    ObjectStorage storage = objectStorage.getIfAvailable();
    if (storage == null || path.isBlank()) {
      return new DialogMessageDownload(true, fileId, name, url, size, null);
    }
    try {
      return new DialogMessageDownload(false, fileId, name, url, size, storage.open(path));
    } catch (BusinessException ex) {
      return new DialogMessageDownload(true, fileId, name, url, size, null);
    }
  }

  /**
   * 契约 {@code GET /api/dialog/message/mergeDetail}：解析合并转发消息内嵌条目。
   */
  public DialogMergeDetailView mergeDetail(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    String type = message.getType() == null ? "" : message.getType();
    if (!"merge".equals(type) && !"merge-forward".equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_MERGE_INVALID);
    }
    List<DialogMessageView> items = parseMergeItems(message.getBody());
    if (items.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_MERGE_INVALID);
    }
    return new DialogMergeDetailView(items);
  }

  /**
   * 契约 {@code GET /api/dialog/message/dot}：清除当前用户对消息的红点。
   */
  @Transactional
  public Map<String, Object> messageDot(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    dialogs.clearMessageDot(messageId, me);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("messageId", messageId);
    out.put("dot", 0);
    return out;
  }

  /**
   * 契约 {@code GET /api/dialog/message/checked}：切换文本消息中第 {@code index} 个 {@code <li>} 的
   * checked 标记（仅本人）。
   */
  @Transactional
  public DialogMessageView messageChecked(long dialogId, long messageId, int index, int checked) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    if (message.getDialogId() != dialogId) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND);
    }
    if (message.getUserId() != me || !"text".equals(message.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_CHECKED_DENIED);
    }
    String newBody = toggleListChecked(message.getBody(), index, checked != 0);
    LocalDateTime now = LocalDateTime.now();
    dialogs.updateMessageBody(messageId, newBody, now);
    message.setBody(newBody);
    message.setUpdatedAt(now);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(message));
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_UPDATE, dialogs.listMemberUserIds(dialogId), data);
    return DialogMessageView.from(message);
  }

  /**
   * 契约 {@code POST /api/dialog/message/stream}：通知指定用户通过 EventSource 监听流式消息。
   */
  public void messageStream(long userId, String streamUrl, String source) {
    AuthContext.requireUserId();
    if (userId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_STREAM_INVALID);
    }
    String url = streamUrl == null ? "" : streamUrl.trim();
    if (url.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_STREAM_INVALID);
    }
    if (!users.existsByUserId(userId)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND);
    }
    String src = source == null || source.isBlank() ? "api" : source.trim();
    if ("ai".equalsIgnoreCase(src)) {
      String path = url.replaceFirst("(?i)^/ai/?", "/");
      if (!path.startsWith("/")) {
        path = "/" + path;
      }
      url = "/ai" + path;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("userId", userId);
    data.put("streamUrl", url);
    data.put("source", src);
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE_STREAM, List.of(userId), data);
  }

  /**
   * 契约 {@code GET /api/dialog/message/mark}：会话标记已读/未读；{@code type=read|unread}。
   */
  @Transactional
  public Map<String, Object> messageMark(long dialogId, String type, Long afterMessageId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    String t = type == null ? "" : type.trim().toLowerCase();
    if (!"read".equals(t) && !"unread".equals(t)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_MARK_INVALID);
    }
    if ("read".equals(t)) {
      LocalDateTime now = LocalDateTime.now();
      long upTo = dialogs.maxMessageId(dialogId);
      long after = afterMessageId == null ? 0L : afterMessageId;
      if (after > 0) {
        dialogs.markMessageReadsFrom(dialogId, me, after, now);
      } else if (upTo > 0) {
        dialogs.markMessageReadsUpTo(dialogId, me, upTo, now);
      }
      dialogs.clearUnread(dialogId, me, upTo);
      dialogs.setMarkUnread(dialogId, me, false);
    } else {
      dialogs.setMarkUnread(dialogId, me, true);
    }
    return unreadSnapshot(dialogId, me);
  }

  /**
   * 契约 {@code GET /api/dialog/message/tag}：消息级标注/取消（存 tagUserId）。
   */
  @Transactional
  public DialogMessageTagView messageTag(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    String type = message.getType() == null ? "" : message.getType();
    if ("tag".equals(type) || "todo".equals(type) || "notice".equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TAG_DENIED);
    }
    long before = message.getTagUserId();
    long next = before > 0 ? 0L : me;
    LocalDateTime now = LocalDateTime.now();
    dialogs.updateMessageTagUserId(messageId, next, now);
    message.setTagUserId(next);
    message.setUpdatedAt(now);
    String action = next > 0 ? "add" : "remove";
    String payload =
        "{\"action\":\""
            + action
            + "\",\"data\":{\"messageId\":"
            + messageId
            + ",\"type\":\""
            + escapeJson(type)
            + "\"}}";
    DialogMessageView add =
        insertOutboundMessage(me, message.getDialogId(), "tag", payload, "[tag]", false);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", message.getDialogId());
    data.put("messageId", messageId);
    data.put("tag", next);
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_UPDATE,
        dialogs.listMemberUserIds(message.getDialogId()),
        data);
    return new DialogMessageTagView(messageId, next, add);
  }

  /**
   * 契约 {@code GET /api/dialog/message/color}：设置当前用户在会话上的颜色标记。
   */
  @Transactional
  public DialogConfigView messageColor(long dialogId, String color) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    String c = color == null ? "" : color.trim();
    if (c.length() > 32) {
      c = c.substring(0, 32);
    }
    dialogs.setMemberColor(dialogId, me, c);
    return toConfigView(dialogId, me);
  }

  /**
   * 契约 {@code GET /api/dialog/message/translation}：翻译文本/语音消息；结果按语言缓存。
   */
  @Transactional
  public DialogMessageTranslationView messageTranslation(
      long messageId, String language, Integer force) {
    long me = AuthContext.requireUserId();
    if (language == null || language.isBlank() || !Messages.isSupportedUserLang(language)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TRANSLATION_LANG);
    }
    String lang = Messages.toUserLang(language);
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    String type = message.getType() == null ? "" : message.getType();
    if (!"text".equals(type) && !"record".equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TRANSLATION_TYPE);
    }
    boolean forceRefresh = force != null && force != 0;
    if (forceRefresh) {
      dialogs.deleteTranslation(messageId, lang);
    } else {
      Optional<Map<String, Object>> cached = dialogs.findTranslation(messageId, lang);
      if (cached.isPresent()) {
        return new DialogMessageTranslationView(
            messageId, lang, String.valueOf(cached.get().getOrDefault("content", "")));
      }
    }
    String sourceText = extractTranslationText(message.getBody());
    if (sourceText.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TRANSLATION_EMPTY);
    }
    AiBotChatService ai = aiBotChat.getIfAvailable();
    if (ai == null || !ai.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TRANSLATION_FAILED);
    }
    String targetName = "en-US".equalsIgnoreCase(lang) ? "English" : "Chinese";
    String translated =
        ai.chat(
            "You are a translator. Translate the user message into "
                + targetName
                + ". Return only the translation, no quotes or explanation.",
            sourceText);
    if (translated == null || translated.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TRANSLATION_FAILED);
    }
    String content = translated.trim();
    LocalDateTime now = LocalDateTime.now();
    dialogs.upsertTranslation(
        IdGenerator.nextId(), message.getDialogId(), messageId, lang, content, now);
    return new DialogMessageTranslationView(messageId, lang, content);
  }

  private Map<String, Object> unreadSnapshot(long dialogId, long userId) {
    Map<String, Object> flags =
        dialogs
            .findMemberFlags(dialogId, userId)
            .orElseGet(
                () -> {
                  Map<String, Object> empty = new LinkedHashMap<>();
                  empty.put("unreadCount", 0);
                  empty.put("mentionCount", 0);
                  empty.put("mentionIds", "");
                  empty.put("lastReadMessageId", 0L);
                  empty.put("markUnread", 0);
                  empty.put("updatedAt", LocalDateTime.now());
                  return empty;
                });
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("dialogId", dialogId);
    out.put("unreadCount", ((Number) flags.getOrDefault("unreadCount", 0)).intValue());
    out.put("mentionCount", ((Number) flags.getOrDefault("mentionCount", 0)).intValue());
    out.put(
        "mentionIds",
        DialogMentionParser.parseIdsCsv(String.valueOf(flags.getOrDefault("mentionIds", ""))));
    out.put("lastReadMessageId", ((Number) flags.getOrDefault("lastReadMessageId", 0L)).longValue());
    out.put("markUnread", ((Number) flags.getOrDefault("markUnread", 0)).intValue());
    out.put("updatedAt", flags.get("updatedAt"));
    return out;
  }

  private static String extractTranslationText(String body) {
    if (body == null || body.isBlank()) {
      return "";
    }
    try {
      JsonNode root = JSON.readTree(body);
      if (root.isObject()) {
        if (root.has("text") && root.get("text").isTextual()) {
          return root.get("text").asString("").trim();
        }
        if (root.has("content") && root.get("content").isTextual()) {
          return root.get("content").asString("").trim();
        }
      }
    } catch (Exception ignored) {
      // plain text
    }
    return body.trim();
  }

  @Transactional
  public void markRead(long dialogId, Long messageId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    long upTo = messageId != null && messageId > 0 ? messageId : dialogs.maxMessageId(dialogId);
    if (upTo <= 0) {
      dialogs.clearUnread(dialogId, userId, 0L);
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    dialogs.markMessageReadsUpTo(dialogId, userId, upTo, now);
    dialogs.clearUnread(dialogId, userId, upTo);
  }

  public List<DialogUnreadItemView> unread() {
    long userId = AuthContext.requireUserId();
    return dialogs.listUnreadByUser(userId).stream()
        .map(
            row ->
                new DialogUnreadItemView(
                    ((Number) row.get("dialogId")).longValue(),
                    ((Number) row.get("unreadCount")).intValue(),
                    ((Number) row.getOrDefault("mentionCount", 0)).intValue(),
                    DialogMentionParser.parseIdsCsv(String.valueOf(row.getOrDefault("mentionIds", ""))),
                    ((Number) row.get("lastReadMessageId")).longValue()))
        .toList();
  }

  public DialogMessageReadListView readList(long messageId) {
    long userId = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), userId);
    List<Long> members = dialogs.listMemberUserIds(message.getDialogId());
    List<Long> reads = dialogs.listReaders(messageId);
    java.util.Set<Long> readSet = new java.util.HashSet<>(reads);
    List<Long> unreads = members.stream().filter(memberUserId -> !readSet.contains(memberUserId)).toList();
    return new DialogMessageReadListView(messageId, reads, unreads);
  }

  @Transactional
  public DialogMessageView sendFileId(long dialogId, long fileId, Long replyId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    assertCanSpeak(dialogId, userId);
    assertNotHumanToBotDm(dialogId, userId);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    Map<String, Object> file =
        dialogs
            .findFileMeta(fileId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_FILE_INVALID));
    long owner = ((Number) file.get("userId")).longValue();
    if (owner != userId) {
      // 仅允许发送自己网盘中的文件（共享可读后续再放宽）
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FILE_INVALID);
    }
    String name = String.valueOf(file.getOrDefault("name", "file"));
    String type = String.valueOf(file.getOrDefault("type", "file"));
    String extension = String.valueOf(file.getOrDefault("extension", ""));
    long size = ((Number) file.getOrDefault("size", 0L)).longValue();
    String path = String.valueOf(file.getOrDefault("path", ""));
    String messageType = "picture".equals(type) ? "image" : "file";
    String payload =
        "{\"fileId\":"
            + fileId
            + ",\"name\":\""
            + escapeJson(name)
            + "\",\"type\":\""
            + escapeJson(type)
            + "\",\"extension\":\""
            + escapeJson(extension)
            + "\",\"size\":"
            + size
            + ",\"path\":\""
            + escapeJson(path)
            + "\"}";

    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(userId);
    m.setType(messageType);
    m.setBody(payload);
    m.setReplyId(replyId == null ? 0L : replyId);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, "[" + messageType + "] " + name, now);
    dialogs.bumpUnreadExcept(dialogId, userId);
    initMessageReads(dialogId, m.getId(), userId, now);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    publishSearchIndex(
        SearchIndexEvent.ACTION_UPSERT,
        SearchIndexEvent.TYPE_MESSAGE,
        m.getId(),
        userId,
        0L,
        name,
        name);
    return DialogMessageView.from(m);
  }

  /**
   * 通过任务 ID 发送任务卡片。契约 {@code GET /api/dialog/message/sendTaskId}。
   *
   * @param note 可选留言（载荷 {@code note}）
   */
  @Transactional
  public DialogMessageView sendTaskId(long dialogId, long taskId, String note, Long replyId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    assertCanSpeak(dialogId, userId);
    assertNotHumanToBotDm(dialogId, userId);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    TaskCardBridge bridge = taskCards.getIfAvailable();
    if (bridge == null) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.TASK_NOT_FOUND);
    }
    Map<String, Object> card = bridge.buildCard(taskId, userId, note);
    String payload;
    try {
      payload = JSON.writeValueAsString(card);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.TASK_NOT_FOUND);
    }
    String title = String.valueOf(card.getOrDefault("name", Messages.get(I18nKeys.TASK_GROUP_DEFAULT_NAME)));
    if ("null".equals(title) || title.isBlank()) {
      title = Messages.get(I18nKeys.TASK_GROUP_DEFAULT_NAME);
    }

    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(userId);
    m.setType("task");
    m.setBody(payload);
    m.setReplyId(replyId == null ? 0L : replyId);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    String shortTitle = title.length() > 40 ? title.substring(0, 40) : title;
    String preview = Messages.get(I18nKeys.TASK_CARD_PREVIEW, shortTitle);
    dialogs.touchDialog(dialogId, preview, now);
    dialogs.bumpUnreadExcept(dialogId, userId);
    initMessageReads(dialogId, m.getId(), userId, now);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    publishSearchIndex(
        SearchIndexEvent.ACTION_UPSERT,
        SearchIndexEvent.TYPE_MESSAGE,
        m.getId(),
        userId,
        0L,
        title,
        note == null || note.isBlank() ? title : note.trim());
    bridge.linkFromDialogIfTaskGroup(dialogId, m.getId(), taskId, userId);
    return DialogMessageView.from(m);
  }

  /**
   * 会话直传文件：落盘写入 {@code bluedock_files} 后发 file/image 消息。
   * 契约 {@code POST /api/dialog/message/sendFile}。
   */
  @Transactional
  public DialogMessageView sendFile(
      long dialogId, String filename, long size, InputStream content, Long replyId) {
    long userId = AuthContext.requireUserId();
    requireMember(dialogId, userId);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    DialogChatFileSink sink = chatFiles.getIfAvailable();
    if (sink == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHAT_SINK_MISSING);
    }
    DialogChatFileSink.Saved saved = sink.save(userId, dialogId, filename, size, content);
    return sendFileId(dialogId, saved.fileId(), replyId);
  }

  /**
   * 群发文件：多文件 × 多会话。契约 {@code POST /api/dialog/message/sendFiles}。
   *
   * <p>每个文件落盘一次（路径挂在首个目标会话），再向各目标会话 {@link #sendFileId}。
   * 上限：文件 ≤20、会话 ≤20。
   */
  @Transactional
  public List<DialogMessageView> sendFiles(List<Long> targetDialogIds, List<ChatFilePart> parts) {
    long userId = AuthContext.requireUserId();
    if (targetDialogIds == null || targetDialogIds.isEmpty() || parts == null || parts.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SEND_FILES_INVALID);
    }
    if (targetDialogIds.size() > 20 || parts.size() > 20) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SEND_FILES_INVALID);
    }
    DialogChatFileSink sink = chatFiles.getIfAvailable();
    if (sink == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHAT_SINK_MISSING);
    }
    for (Long tid : targetDialogIds) {
      if (tid == null || tid <= 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SEND_FILES_INVALID);
      }
      requireMember(tid, userId);
      if (dialogs.findActive(tid).isEmpty()) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
      }
      assertCanSpeak(tid, userId);
      assertNotHumanToBotDm(tid, userId);
    }
    long anchorDialogId = targetDialogIds.get(0);
    List<DialogMessageView> out = new ArrayList<>();
    for (ChatFilePart part : parts) {
      if (part == null || part.filename() == null || part.filename().isBlank() || part.content() == null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SEND_FILES_INVALID);
      }
      if (part.size() <= 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
      }
      DialogChatFileSink.Saved saved =
          sink.save(
              userId,
              anchorDialogId,
              part.filename().trim(),
              part.size(),
              new ByteArrayInputStream(part.content()));
      for (Long tid : targetDialogIds) {
        out.add(sendFileId(tid, saved.fileId(), null));
      }
    }
    return out;
  }

  /** 群发直传的一份文件内容（Controller 已读入内存，避免多目标重读流）。 */
  public record ChatFilePart(String filename, long size, byte[] content) {}

  private static final int MAX_IMAGE64_BYTES = 5 * 1024 * 1024;

  /**
   * Base64 / data-URL 发图。契约 {@code POST /api/dialog/message/image64}。
   *
   * @param image base64 或 {@code data:image/png;base64,...}
   * @param filename 可选；缺省按 MIME 生成 {@code image.png} 等
   */
  @Transactional
  public DialogMessageView sendImage64(
      long dialogId, String image, String filename, Long replyId) {
    DecodedImage decoded = decodeImage64(image);
    String name =
        filename != null && !filename.isBlank()
            ? filename.trim()
            : ("image." + decoded.extension());
    if (!name.contains(".")) {
      name = name + "." + decoded.extension();
    }
    return sendFile(
        dialogId, name, decoded.bytes().length, new ByteArrayInputStream(decoded.bytes()), replyId);
  }

  /**
   * 在线表情：服务端拉取 {@code src} 后按图片消息发送。契约 {@code POST /api/dialog/message/sendSticker}。
   */
  @Transactional
  public DialogMessageView sendSticker(long dialogId, String src, String name, Long replyId) {
    StickerSearchService.StickerBytes downloaded = stickerSearch.download(src);
    if (downloaded == null) {
      String value = src == null ? "" : src.trim();
      if (value.isEmpty()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_STICKER_INVALID);
      }
      try {
        StickerSearchService.validatePublicHttpUrl(value);
      } catch (IllegalArgumentException e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_STICKER_INVALID);
      }
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_STICKER_FETCH_FAILED);
    }
    if (downloaded.bytes().length > MAX_IMAGE64_BYTES) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_TOO_LARGE, MAX_IMAGE64_BYTES);
    }
    String filename =
        name != null && !name.isBlank()
            ? sanitizeStickerFilename(name.trim(), downloaded.extension())
            : ("sticker." + downloaded.extension());
    return sendFile(
        dialogId,
        filename,
        downloaded.bytes().length,
        new ByteArrayInputStream(downloaded.bytes()),
        replyId);
  }

  private static String sanitizeStickerFilename(String raw, String extension) {
    String base = raw.replaceAll("[\\\\/\\s]+", "_");
    if (base.length() > 80) {
      base = base.substring(0, 80);
    }
    if (base.isBlank()) {
      base = "sticker";
    }
    if (!base.contains(".")) {
      return base + "." + extension;
    }
    return base;
  }

  private static DecodedImage decodeImage64(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_INVALID);
    }
    String ext = "png";
    String b64 = value;
    if (value.startsWith("data:")) {
      int comma = value.indexOf(',');
      if (comma < 0) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_INVALID);
      }
      String meta = value.substring(5, comma).toLowerCase(java.util.Locale.ROOT);
      b64 = value.substring(comma + 1);
      if (meta.contains("image/jpeg") || meta.contains("image/jpg")) {
        ext = "jpg";
      } else if (meta.contains("image/webp")) {
        ext = "webp";
      } else if (meta.contains("image/gif")) {
        ext = "gif";
      } else if (meta.contains("image/bmp")) {
        ext = "bmp";
      } else if (meta.contains("image/png")) {
        ext = "png";
      } else if (!meta.startsWith("image/")) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_INVALID);
      }
    }
    byte[] bytes;
    try {
      bytes = java.util.Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_INVALID);
    }
    if (bytes.length == 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_INVALID);
    }
    if (bytes.length > MAX_IMAGE64_BYTES) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_IMAGE64_TOO_LARGE, MAX_IMAGE64_BYTES);
    }
    return new DecodedImage(ext, bytes);
  }

  private record DecodedImage(String extension, byte[] bytes) {}

  private void initMessageReads(long dialogId, long messageId, long senderId, LocalDateTime now) {
    initMessageReads(dialogId, messageId, senderId, now, null);
  }

  private void initMessageReads(
      long dialogId, long messageId, long senderId, LocalDateTime now, String messageType) {
    Map<Long, Boolean> mutes = dialogs.listMemberMutes(dialogId);
    int dotForOthers = "record".equals(messageType) ? 1 : 0;
    for (Long userId : dialogs.listMemberUserIds(dialogId)) {
      LocalDateTime readAt = userId.equals(senderId) ? now : null;
      boolean silence = Boolean.TRUE.equals(mutes.get(userId));
      int dot = userId.equals(senderId) ? 0 : dotForOthers;
      try {
        dialogs.insertMessageRead(
            IdGenerator.nextId(), messageId, dialogId, userId, readAt, silence, dot);
      } catch (Exception ignored) {
        // 唯一键冲突忽略
      }
    }
  }

  private static String toggleListChecked(String body, int index, boolean checked) {
    if (body == null || body.isEmpty() || index < 0) {
      return body == null ? "" : body;
    }
    try {
      JsonNode root = JSON.readTree(body);
      if (root.isObject() && root.has("text") && root.get("text").isTextual()) {
        String text = root.get("text").asString("");
        String updated = replaceListItemMark(text, index, checked);
        ObjectNode copy = ((ObjectNode) root).deepCopy();
        copy.put("text", updated);
        return JSON.writeValueAsString(copy);
      }
    } catch (Exception ignored) {
      // 非 JSON，按纯文本/HTML 处理
    }
    return replaceListItemMark(body, index, checked);
  }

  private static String replaceListItemMark(String text, int index, boolean checked) {
    java.util.regex.Pattern pattern =
        java.util.regex.Pattern.compile("(?i)<li\\b[^>]*>");
    java.util.regex.Matcher matcher = pattern.matcher(text);
    StringBuilder out = new StringBuilder();
    int i = 0;
    int last = 0;
    String mark = checked ? "<li data-list=\"checked\">" : "<li data-list=\"unchecked\">";
    while (matcher.find()) {
      out.append(text, last, matcher.start());
      if (i++ == index) {
        out.append(mark);
      } else {
        out.append(matcher.group());
      }
      last = matcher.end();
    }
    out.append(text, last, text.length());
    return out.toString();
  }

  /**
   * 逐条转发到目标会话；{@code messageIds} 与 {@code dialogIds} 均为逗号分隔。
   *
   * @return 各目标会话新产生的消息（扁平列表）
   */
  @Transactional
  public List<DialogMessageView> forward(String messageIds, String dialogIds) {
    long me = AuthContext.requireUserId();
    List<Long> sources = parseIds(messageIds);
    List<Long> targets = parseIds(dialogIds);
    if (sources.isEmpty() || targets.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
    }
    if (sources.size() > 20 || targets.size() > 20) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
    }
    List<DialogMessage> messages = dialogs.findMessagesByIds(sources);
    if (messages.size() != sources.size()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND);
    }
    for (DialogMessage src : messages) {
      requireMember(src.getDialogId(), me);
    }
    for (Long tid : targets) {
      requireMember(tid, me);
      if (dialogs.findActive(tid).isEmpty()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
      }
    }

    List<DialogMessageView> out = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (Long tid : targets) {
      for (DialogMessage src : messages) {
        String payload =
            "{\"forward\":true,\"fromMessageId\":"
                + src.getId()
                + ",\"fromDialogId\":"
                + src.getDialogId()
                + ",\"fromUserId\":"
                + src.getUserId()
                + ",\"type\":\""
                + escapeJson(src.getType())
                + "\",\"body\":"
                + jsonStringValue(src.getBody())
                + "}";
        DialogMessage m = new DialogMessage();
        m.setId(IdGenerator.nextId());
        m.setDialogId(tid);
        m.setUserId(me);
        m.setType("forward");
        m.setBody(payload);
        m.setReplyId(0L);
        m.setCreatedAt(now);
        dialogs.insertMessage(m);
        String preview =
            src.getBody() == null
                ? "[forward]"
                : (src.getBody().length() > 80 ? src.getBody().substring(0, 80) : src.getBody());
        dialogs.touchDialog(tid, preview, now);
        dialogs.bumpUnreadExcept(tid, me);
        initMessageReads(tid, m.getId(), me, now);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dialogId", tid);
        data.put("message", DialogMessageView.from(m));
        publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(tid), data);
        out.add(DialogMessageView.from(m));
      }
    }
    return out;
  }

  /** 多条合并为一条 merge 消息转发到单个目标会话。 */
  @Transactional
  public DialogMessageView mergeForward(String messageIds, long dialogId) {
    long me = AuthContext.requireUserId();
    List<Long> sources = parseIds(messageIds);
    if (sources.isEmpty() || sources.size() > 50) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
    }
    requireMember(dialogId, me);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    List<DialogMessage> messages = dialogs.findMessagesByIds(sources);
    if (messages.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND);
    }
    for (DialogMessage src : messages) {
      requireMember(src.getDialogId(), me);
    }
    StringBuilder items = new StringBuilder("[");
    for (int i = 0; i < messages.size(); i++) {
      DialogMessage src = messages.get(i);
      if (i > 0) {
        items.append(',');
      }
      items
          .append("{\"messageId\":")
          .append(src.getId())
          .append(",\"userId\":")
          .append(src.getUserId())
          .append(",\"type\":\"")
          .append(escapeJson(src.getType()))
          .append("\",\"body\":")
          .append(jsonStringValue(src.getBody()))
          .append('}');
    }
    items.append(']');
    String payload = "{\"merge\":true,\"items\":" + items + "}";
    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(me);
    m.setType("merge");
    m.setBody(payload);
    m.setReplyId(0L);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, "[merge] " + messages.size() + " messages", now);
    dialogs.bumpUnreadExcept(dialogId, me);
    initMessageReads(dialogId, m.getId(), me, now);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    return DialogMessageView.from(m);
  }

  /**
   * 表情回复；{@code cancel=1} 取消。返回该消息当前表情聚合。
   */
  @Transactional
  public List<DialogMessageEmojiView> emoji(long messageId, String symbol, boolean cancel) {
    long me = AuthContext.requireUserId();
    String sym = symbol == null ? "" : symbol.trim();
    if (sym.isEmpty() || sym.length() > 32) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_EMOJI_INVALID);
    }
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    if (cancel) {
      dialogs.deleteEmoji(messageId, me, sym);
    } else {
      try {
        dialogs.insertEmoji(IdGenerator.nextId(), messageId, me, sym, LocalDateTime.now());
      } catch (Exception ignored) {
        // 已存在则幂等
      }
    }
    List<DialogMessageEmojiView> views = aggregateEmojis(messageId);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", message.getDialogId());
    data.put("messageId", messageId);
    data.put("emojis", views);
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_EMOJI, dialogs.listMemberUserIds(message.getDialogId()), data);
    return views;
  }

  public List<DialogMessageEmojiView> emojiList(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    return aggregateEmojis(messageId);
  }

  /**
   * 契约 {@code GET /api/dialog/message/emojiMap}：批量表情聚合；`messageIds` 逗号分隔，最多 100。
   * → `[{ messageId, emojis: DialogMessageEmojiView[] }]`
   */
  public List<Map<String, Object>> emojiMap(String messageIds) {
    long me = AuthContext.requireUserId();
    List<Long> ids = parseIds(messageIds);
    if (ids.isEmpty()) {
      return List.of();
    }
    if (ids.size() > 100) {
      ids = ids.subList(0, 100);
    }
    List<DialogMessage> messages = dialogs.findMessagesByIds(ids);
    if (messages.isEmpty()) {
      return List.of();
    }
    long dialogId = messages.get(0).getDialogId();
    for (DialogMessage m : messages) {
      if (m.getDialogId() != dialogId) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_EMOJI_INVALID);
      }
    }
    requireMember(dialogId, me);

    Map<Long, Map<String, List<Long>>> byMsgSym = new LinkedHashMap<>();
    Map<Long, Map<String, LocalDateTime>> byMsgFirst = new LinkedHashMap<>();
    for (Map<String, Object> row : dialogs.listEmojisByMessageIds(ids)) {
      long messageId = ((Number) row.get("messageId")).longValue();
      String sym = String.valueOf(row.get("symbol"));
      long userId = ((Number) row.get("userId")).longValue();
      byMsgSym
          .computeIfAbsent(messageId, k -> new LinkedHashMap<>())
          .computeIfAbsent(sym, k -> new ArrayList<>())
          .add(userId);
      byMsgFirst
          .computeIfAbsent(messageId, k -> new LinkedHashMap<>())
          .putIfAbsent(sym, (LocalDateTime) row.get("createdAt"));
    }

    List<Map<String, Object>> out = new ArrayList<>();
    for (Long messageId : ids) {
      Map<String, List<Long>> bySym = byMsgSym.getOrDefault(messageId, Map.of());
      Map<String, LocalDateTime> firstAt = byMsgFirst.getOrDefault(messageId, Map.of());
      List<DialogMessageEmojiView> emojis = new ArrayList<>();
      for (Map.Entry<String, List<Long>> e : bySym.entrySet()) {
        emojis.add(new DialogMessageEmojiView(e.getKey(), e.getValue(), firstAt.get(e.getKey())));
      }
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("messageId", messageId);
      item.put("emojis", emojis);
      out.add(item);
    }
    return out;
  }

  /** 消息置顶 / 取消；同一会话可多条，按时间倒序。 */
  @Transactional
  public List<DialogMessageView> messageTop(long messageId, boolean cancel) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    if (cancel) {
      dialogs.deleteMessageTop(message.getDialogId(), messageId);
    } else {
      try {
        dialogs.insertMessageTop(
            IdGenerator.nextId(), message.getDialogId(), messageId, me, LocalDateTime.now());
      } catch (Exception ignored) {
        // 已置顶则幂等
      }
    }
    List<DialogMessageView> tops = topInfo(message.getDialogId());
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", message.getDialogId());
    data.put("messageId", messageId);
    data.put("cancel", cancel);
    data.put("tops", tops);
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_TOP, dialogs.listMemberUserIds(message.getDialogId()), data);
    return tops;
  }

  public List<DialogMessageView> topInfo(long dialogId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    List<Long> ids = dialogs.listTopMessageIds(dialogId);
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, DialogMessage> byId = new LinkedHashMap<>();
    for (DialogMessage m : dialogs.findMessagesByIds(ids)) {
      byId.put(m.getId(), m);
    }
    List<DialogMessageView> out = new ArrayList<>();
    for (Long id : ids) {
      DialogMessage m = byId.get(id);
      if (m != null) {
        out.add(DialogMessageView.from(m));
      }
    }
    return out;
  }

  /**
   * 设/取消待办。notice/todo 类型不可再设。已完成不可取消。
   */
  @Transactional
  public DialogMessageTodoView todo(long messageId, boolean cancel) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    String type = message.getType() == null ? "" : message.getType();
    if ("notice".equals(type) || "todo".equals(type) || "tag".equals(type)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TODO_DENIED);
    }
    Optional<Map<String, Object>> existing = dialogs.findTodo(messageId, me);
    if (cancel) {
      if (existing.isPresent() && existing.get().get("doneAt") != null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TODO_DONE);
      }
      dialogs.deleteTodo(messageId, me);
      publishTodoEvent(message.getDialogId(), messageId, "cancel", null);
      return null;
    }
    if (existing.isPresent()) {
      return toTodoView(existing.get());
    }
    LocalDateTime now = LocalDateTime.now();
    long id = IdGenerator.nextId();
    dialogs.insertTodo(id, messageId, message.getDialogId(), me, now);
    DialogMessageTodoView view =
        new DialogMessageTodoView(id, messageId, message.getDialogId(), me, null, null, now);
    publishTodoEvent(message.getDialogId(), messageId, "add", view);
    return view;
  }

  public List<DialogMessageTodoView> todoList(Long dialogId, boolean includeDone) {
    long me = AuthContext.requireUserId();
    if (dialogId != null && dialogId > 0) {
      requireMember(dialogId, me);
    }
    return dialogs.listTodos(me, dialogId, includeDone).stream().map(this::toTodoView).toList();
  }

  /** 契约 {@code GET /api/dialog/todo}：当前用户在指定会话的待办（默认未完成）。 */
  public List<DialogMessageTodoView> dialogTodos(long dialogId, boolean includeDone) {
    if (dialogId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_NOT_FOUND);
    }
    return todoList(dialogId, includeDone);
  }

  /**
   * 契约 {@code GET /api/dialog/open/event}：打开会话事件（与 {@link #one} 相同，含 bot dialogOpen）。
   */
  public DialogView openEvent(long dialogId) {
    return one(dialogId);
  }

  /** 契约 {@code GET /api/dialog/session/create}：在会话下开启新的 AI 会话并设为当前。 */
  @Transactional
  public DialogSessionView sessionCreate(long dialogId, String title) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    String t = title == null ? "" : title.trim();
    if (t.length() > 255) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SESSION_INVALID);
    }
    if (t.isEmpty()) {
      t = "New chat";
    }
    String sessionKey = UUID.randomUUID().toString().replace("-", "");
    LocalDateTime now = LocalDateTime.now();
    dialogs.insertDialogSession(IdGenerator.nextId(), dialogId, me, sessionKey, t, now);
    dialogs.setUserSessionKey(dialogId, me, sessionKey);
    return new DialogSessionView(dialogId, sessionKey, t, 1, now, now);
  }

  /** 契约 {@code GET /api/dialog/session/list}。 */
  public List<DialogSessionView> sessionList(long dialogId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    String current = dialogs.findUserSessionKey(dialogId, me);
    return dialogs.listDialogSessions(dialogId, me).stream()
        .map(
            row ->
                toSessionView(
                    row, current.equals(String.valueOf(row.getOrDefault("sessionKey", "")))))
        .toList();
  }

  /** 契约 {@code GET /api/dialog/session/open}：切换当前 AI 会话。 */
  @Transactional
  public DialogSessionView sessionOpen(long dialogId, String sessionId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    String key = sessionId == null ? "" : sessionId.trim();
    if (key.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SESSION_INVALID);
    }
    Map<String, Object> row =
        dialogs
            .findDialogSession(dialogId, me, key)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_SESSION_NOT_FOUND));
    LocalDateTime now = LocalDateTime.now();
    dialogs.setUserSessionKey(dialogId, me, key);
    dialogs.touchDialogSession(dialogId, me, key, now);
    row.put("updatedAt", now);
    return toSessionView(row, true);
  }

  /** 契约 {@code POST /api/dialog/session/rename}。 */
  @Transactional
  public DialogSessionView sessionRename(long dialogId, String sessionId, String title) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    String key = sessionId == null ? "" : sessionId.trim();
    String t = title == null ? "" : title.trim();
    if (key.isEmpty() || t.isEmpty() || t.length() > 255) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SESSION_INVALID);
    }
    LocalDateTime now = LocalDateTime.now();
    int n = dialogs.updateDialogSessionTitle(dialogId, me, key, t, now);
    if (n == 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_SESSION_NOT_FOUND);
    }
    Map<String, Object> row = dialogs.findDialogSession(dialogId, me, key).orElseThrow();
    String current = dialogs.findUserSessionKey(dialogId, me);
    return toSessionView(row, current.equals(key));
  }

  private static DialogSessionView toSessionView(Map<String, Object> row, boolean current) {
    return new DialogSessionView(
        ((Number) row.get("dialogId")).longValue(),
        String.valueOf(row.getOrDefault("sessionKey", "")),
        String.valueOf(row.getOrDefault("title", "")),
        current ? 1 : 0,
        (LocalDateTime) row.get("createdAt"),
        (LocalDateTime) row.get("updatedAt"));
  }

  @Transactional
  public DialogMessageTodoView todoDone(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    LocalDateTime now = LocalDateTime.now();
    int n = dialogs.markTodoDone(messageId, me, now);
    if (n == 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND);
    }
    DialogMessageTodoView view =
        toTodoView(
            dialogs
                .findTodo(messageId, me)
                .orElseThrow(
                    () ->
                        new BusinessException(
                            ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND)));
    publishTodoEvent(message.getDialogId(), messageId, "done", view);
    return view;
  }

  @Transactional
  public DialogMessageTodoView todoRemind(long messageId, String remindAt) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    Optional<Map<String, Object>> existing = dialogs.findTodo(messageId, me);
    if (existing.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND);
    }
    if (existing.get().get("doneAt") != null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TODO_DONE);
    }
    LocalDateTime remind = parseRemindAt(remindAt);
    LocalDateTime now = LocalDateTime.now();
    dialogs.updateTodoRemind(messageId, me, remind, now);
    DialogMessageTodoView view = toTodoView(dialogs.findTodo(messageId, me).orElseThrow());
    publishTodoEvent(message.getDialogId(), messageId, "remind", view);
    return view;
  }

  private void publishTodoEvent(
      long dialogId, long messageId, String action, DialogMessageTodoView todo) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("messageId", messageId);
    data.put("action", action);
    if (todo != null) {
      data.put("todo", todo);
    }
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE_TODO, dialogs.listMemberUserIds(dialogId), data);
  }

  private DialogMessageTodoView toTodoView(Map<String, Object> row) {
    return new DialogMessageTodoView(
        ((Number) row.get("id")).longValue(),
        ((Number) row.get("messageId")).longValue(),
        ((Number) row.get("dialogId")).longValue(),
        ((Number) row.get("userId")).longValue(),
        (LocalDateTime) row.get("remindAt"),
        (LocalDateTime) row.get("doneAt"),
        (LocalDateTime) row.get("createdAt"));
  }

  private static LocalDateTime parseRemindAt(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String s = raw.trim().replace(' ', 'T');
    try {
      if (s.length() == 16) {
        return LocalDateTime.parse(s + ":00");
      }
      return LocalDateTime.parse(s);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TODO_DENIED);
    }
  }

  /**
   * 发起投票：{@code dialogId}+{@code title}+{@code options}（逗号分隔）。
   * 投票：{@code messageId}+{@code option}（下标，多选逗号分隔）。
   * 结束：{@code messageId}+{@code end=1}（仅发起人）。
   */
  @Transactional
  public DialogMessageView vote(
      Long dialogId, String title, String options, Long messageId, String option, Boolean end) {
    long me = AuthContext.requireUserId();
    if (messageId != null && messageId > 0) {
      return voteAct(me, messageId, option, end != null && end);
    }
    if (dialogId == null || dialogId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    requireMember(dialogId, me);
    assertCanSpeak(dialogId, me);
    assertNotHumanToBotDm(dialogId, me);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    String t = title == null ? "" : title.trim();
    if (t.isEmpty() || t.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    List<String> opts = parseOptionTexts(options);
    if (opts.size() < 2 || opts.size() > 20) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    ObjectNode root = JSON.createObjectNode();
    root.put("title", t);
    root.put("multiple", false);
    root.put("ended", false);
    ArrayNode arr = root.putArray("options");
    for (String o : opts) {
      ObjectNode opt = arr.addObject();
      opt.put("text", o);
      opt.putArray("votes");
    }
    String body = root.toString();
    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(me);
    m.setType("vote");
    m.setBody(body);
    m.setReplyId(0L);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, "[vote] " + t, now);
    dialogs.bumpUnreadExcept(dialogId, me);
    initMessageReads(dialogId, m.getId(), me, now);
    publishNewMessage(dialogId, m);
    return DialogMessageView.from(m);
  }

  private DialogMessageView voteAct(long me, long messageId, String option, boolean end) {
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    if (!"vote".equals(message.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    ObjectNode root;
    try {
      root = (ObjectNode) JSON.readTree(message.getBody() == null ? "{}" : message.getBody());
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    if (end) {
      if (message.getUserId() != me) {
        throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_DENIED);
      }
      root.put("ended", true);
      return saveInteractiveMessage(message, root);
    }
    if (root.path("ended").asBoolean(false)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_ENDED);
    }
    ArrayNode opts = (ArrayNode) root.get("options");
    if (opts == null || opts.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    for (JsonNode o : opts) {
      for (JsonNode v : o.path("votes")) {
        if (v.asLong() == me) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_DUP);
        }
      }
    }
    List<Integer> indexes = parseOptionIndexes(option, opts.size());
    boolean multiple = root.path("multiple").asBoolean(false);
    if (!multiple && indexes.size() != 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    for (int idx : indexes) {
      ((ArrayNode) opts.get(idx).get("votes")).add(me);
    }
    return saveInteractiveMessage(message, root);
  }

  /**
   * 发起接龙：{@code dialogId}+{@code title}。
   * 参与：{@code messageId}+{@code text}。
   * 停止：{@code messageId}+{@code stop=1}（仅发起人）。
   */
  @Transactional
  public DialogMessageView wordChain(
      Long dialogId, String title, Long messageId, String text, Boolean stop) {
    long me = AuthContext.requireUserId();
    if (messageId != null && messageId > 0) {
      return wordChainAct(me, messageId, text, stop != null && stop);
    }
    if (dialogId == null || dialogId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    requireMember(dialogId, me);
    assertCanSpeak(dialogId, me);
    assertNotHumanToBotDm(dialogId, me);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    String t = title == null ? "" : title.trim();
    if (t.isEmpty() || t.length() > 200) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    ObjectNode root = JSON.createObjectNode();
    root.put("title", t);
    root.put("stopped", false);
    root.putArray("items");
    String body = root.toString();
    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(me);
    m.setType("wordChain");
    m.setBody(body);
    m.setReplyId(0L);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, "[wordChain] " + t, now);
    dialogs.bumpUnreadExcept(dialogId, me);
    initMessageReads(dialogId, m.getId(), me, now);
    publishNewMessage(dialogId, m);
    return DialogMessageView.from(m);
  }

  private DialogMessageView wordChainAct(long me, long messageId, String text, boolean stop) {
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    if (!"wordChain".equals(message.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    ObjectNode root;
    try {
      root = (ObjectNode) JSON.readTree(message.getBody() == null ? "{}" : message.getBody());
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    if (stop) {
      if (message.getUserId() != me) {
        throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_DENIED);
      }
      root.put("stopped", true);
      return saveInteractiveMessage(message, root);
    }
    if (root.path("stopped").asBoolean(false)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_WORD_CHAIN_STOPPED);
    }
    String line = text == null ? "" : text.trim();
    if (line.isEmpty() || line.length() > 2000) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    ArrayNode items = root.withArray("items");
    int total = root.path("title").asString("").length();
    for (JsonNode it : items) {
      total += it.path("text").asString("").length();
    }
    if (total + line.length() > MAX_WORDCHAIN_CHARS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_WORD_CHAIN_TOO_LONG);
    }
    ObjectNode item = items.addObject();
    item.put("userId", me);
    item.put("text", line);
    item.put("at", LocalDateTime.now().toString());
    return saveInteractiveMessage(message, root);
  }

  private DialogMessageView saveInteractiveMessage(DialogMessage message, ObjectNode root) {
    LocalDateTime now = LocalDateTime.now();
    String body = root.toString();
    dialogs.updateMessageBody(message.getId(), body, now);
    message.setBody(body);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", message.getDialogId());
    data.put("message", DialogMessageView.from(message));
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_UPDATE,
        dialogs.listMemberUserIds(message.getDialogId()),
        data);
    return DialogMessageView.from(message);
  }

  private void publishNewMessage(long dialogId, DialogMessage m) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
  }

  private static List<String> parseOptionTexts(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : raw.split("[,|]")) {
      String t = part.trim();
      if (!t.isEmpty() && t.length() <= 100) {
        out.add(t);
      }
    }
    return out;
  }

  private static List<Integer> parseOptionIndexes(String raw, int size) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    List<Integer> out = new ArrayList<>();
    for (String part : raw.split("[,;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        int idx = Integer.parseInt(part.trim());
        if (idx < 0 || idx >= size) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
        }
        if (!out.contains(idx)) {
          out.add(idx);
        }
      } catch (NumberFormatException e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
      }
    }
    if (out.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_VOTE_INVALID);
    }
    return out;
  }

  private List<DialogMessageEmojiView> aggregateEmojis(long messageId) {
    Map<String, List<Long>> bySym = new LinkedHashMap<>();
    Map<String, LocalDateTime> firstAt = new LinkedHashMap<>();
    for (Map<String, Object> row : dialogs.listEmojis(messageId)) {
      String sym = String.valueOf(row.get("symbol"));
      long userId = ((Number) row.get("userId")).longValue();
      bySym.computeIfAbsent(sym, k -> new ArrayList<>()).add(userId);
      firstAt.putIfAbsent(sym, (LocalDateTime) row.get("createdAt"));
    }
    List<DialogMessageEmojiView> out = new ArrayList<>();
    for (Map.Entry<String, List<Long>> e : bySym.entrySet()) {
      out.add(new DialogMessageEmojiView(e.getKey(), e.getValue(), firstAt.get(e.getKey())));
    }
    return out;
  }

  private static String jsonStringValue(String raw) {
    if (raw == null) {
      return "\"\"";
    }
    String t = raw.trim();
    if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
      return t;
    }
    return "\"" + escapeJson(raw) + "\"";
  }

  private static List<Long> parseIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
    for (String part : raw.split("[,;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        long v = Long.parseLong(part.trim());
        if (v > 0) {
          ids.add(v);
        }
      } catch (NumberFormatException e) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
      }
    }
    return new ArrayList<>(ids);
  }

  private static String escapeJson(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * 契约 {@code POST /api/dialog/okr/add}：创建或复用 OKR 评论群（{@code group_type=okr}，{@code link_id=okrId}）。
   * 成员含调用者与 {@code userIds}；自动加入 {@code okr-alert@bot.system}。
   */
  @Transactional
  public DialogView okrAdd(long okrId, String name, String userIds) {
    if (okrId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OKR_INVALID);
    }
    long me = AuthContext.requireUserId();
    List<Long> members = parseUserIds(userIds);
    if (!members.contains(me)) {
      members.add(0, me);
    }
    members = members.stream().distinct().toList();
    for (Long userId : members) {
      if (!users.existsByUserId(userId)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND_ID, userId);
      }
      assertNotBotUser(userId);
    }
    String title = name == null ? "" : name.trim();
    if (title.isEmpty()) {
      title = "OKR";
    }
    if (title.length() > 100) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_NAME);
    }

    Dialog existing = dialogs.findByGroupLink("okr", okrId).orElse(null);
    long dialogId;
    if (existing == null) {
      LocalDateTime now = LocalDateTime.now();
      Dialog d = new Dialog();
      d.setId(IdGenerator.nextId());
      d.setType("group");
      d.setGroupType("okr");
      d.setName(title);
      d.setAvatar("");
      d.setOwnerId(me);
      d.setLinkId(okrId);
      d.setLastMessage("");
      d.setLastAt(now);
      d.setCreatedAt(now);
      d.setUnreadCount(0);
      d.setIsTop(0);
      dialogs.insertDialog(d);
      dialogId = d.getId();
    } else {
      dialogId = existing.getId();
      dialogs.updateDialogMeta(dialogId, title, existing.getAvatar() == null ? "" : existing.getAvatar());
      if (existing.getOwnerId() <= 0) {
        dialogs.updateOwner(dialogId, me);
      }
    }

    Set<Long> want = new HashSet<>(members);
    long botUserId = requireOkrAlertBotUserId();
    want.add(botUserId);
    for (Long userId : want) {
      if (!dialogs.isMember(dialogId, userId)) {
        dialogs.insertMember(IdGenerator.nextId(), dialogId, userId);
      }
    }
    for (Long userId : dialogs.listMemberUserIds(dialogId)) {
      if (!want.contains(userId)) {
        dialogs.deleteMember(dialogId, userId);
      }
    }
    return one(dialogId);
  }

  /**
   * 契约 {@code POST /api/dialog/okr/push}：以 OKR 提醒机器人向评论群发文本。
   * 须 {@code dialogId} 或 {@code okrId} 之一；调用者须为会话成员。
   */
  @Transactional
  public DialogMessageView okrPush(Long dialogId, Long okrId, String text) {
    long me = AuthContext.requireUserId();
    Dialog d = resolveOkrDialog(dialogId, okrId);
    requireMember(d.getId(), me);
    long botUserId = requireOkrAlertBotUserId();
    if (!dialogs.isMember(d.getId(), botUserId)) {
      dialogs.insertMember(IdGenerator.nextId(), d.getId(), botUserId);
    }
    return sendMarkdownAsBot(botUserId, d.getId(), text);
  }

  private Dialog resolveOkrDialog(Long dialogId, Long okrId) {
    if (dialogId != null && dialogId > 0) {
      Dialog d =
          dialogs
              .findActive(dialogId)
              .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
      if (!"group".equals(d.getType()) || !"okr".equals(d.getGroupType())) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OKR_INVALID);
      }
      return d;
    }
    if (okrId != null && okrId > 0) {
      return dialogs
          .findByGroupLink("okr", okrId)
          .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OKR_INVALID);
  }

  private long requireOkrAlertBotUserId() {
    return requireBotUserId("okr-alert", "OKR 提醒", false);
  }

  /**
   * 契约 {@code GET /api/dialog/telephone}：单聊对方联系电话；临时账号不可查；成功附带 notice。
   */
  @Transactional
  public DialogTelephoneView telephone(long dialogId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    Dialog d =
        dialogs
            .findActive(dialogId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
    if (!"user".equals(d.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TYPE_USER_ONLY);
    }
    long peerUserId = 0L;
    for (Long memberId : dialogs.listMemberUserIds(dialogId)) {
      if (memberId != null && memberId != me) {
        peerUserId = memberId;
        break;
      }
    }
    if (peerUserId <= 0) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_PEER_REQUIRED);
    }
    UserAccount peer =
        users
            .findByUserId(peerUserId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    String telephone = peer.getTelephone() == null ? "" : peer.getTelephone().trim();
    if (telephone.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TELEPHONE_EMPTY);
    }
    UserAccount meUser =
        users
            .findByUserId(me)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (hasIdentityTag(meUser.getIdentity(), "temporary")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TELEPHONE_DENIED);
    }
    String notice =
        Messages.get(
            I18nKeys.DIALOG_TELEPHONE_VIEWED, displayNickname(meUser), displayNickname(peer));
    String payload = "{\"notice\":\"" + escapeJson(notice) + "\",\"source\":\"telephone\"}";
    DialogMessageView add = insertOutboundMessage(me, dialogId, "notice", payload, notice, false);
    return new DialogTelephoneView(telephone, add);
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendNotice}：当前用户向会话发 notice；支持 {@code dialogIds} 批量。
   */
  @Transactional
  public DialogMessageView sendNotice(
      Long dialogId, String dialogIds, String notice, String silence, String source) {
    long me = AuthContext.requireUserId();
    String body = notice == null ? "" : notice.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (body.length() > MAX_NOTICE_CHARS) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_NOTICE_TOO_LONG, MAX_NOTICE_CHARS);
    }
    List<Long> targets = parseIds(dialogIds);
    if (targets.isEmpty() && dialogId != null && dialogId > 0) {
      targets = List.of(dialogId);
    }
    if (targets.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
    }
    boolean silent = isTruthy(silence);
    String src = source == null || source.isBlank() ? "api" : source.trim();
    String payload = "{\"notice\":\"" + escapeJson(body) + "\",\"source\":\"" + escapeJson(src) + "\"}";
    DialogMessageView last = null;
    for (Long tid : targets) {
      requireMember(tid, me);
      assertCanSpeak(tid, me);
      last = insertOutboundMessage(me, tid, "notice", payload, body, silent);
    }
    return last;
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendTemplate}：向会话发模板卡片；支持 {@code dialogIds} 批量。
   */
  @Transactional
  public DialogMessageView sendTemplate(
      Long dialogId, String dialogIds, String content, String title, String silence, String source) {
    long me = AuthContext.requireUserId();
    List<Map<String, String>> items = parseTemplateContent(content);
    String resolvedTitle = title == null ? "" : title.trim();
    if (resolvedTitle.isEmpty()) {
      resolvedTitle = cutChars(items.getFirst().get("content"), MAX_TEMPLATE_TITLE_CHARS);
    }
    String src = source == null || source.isBlank() ? "api" : source.trim();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "content");
    payload.put("title", resolvedTitle);
    payload.put("content", items);
    payload.put("source", src);
    String body = writeJson(payload);
    boolean silent = isTruthy(silence);
    List<Long> targets = parseIds(dialogIds);
    if (targets.isEmpty() && dialogId != null && dialogId > 0) {
      targets = List.of(dialogId);
    }
    if (targets.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_FORWARD_TARGET);
    }
    DialogMessageView last = null;
    for (Long tid : targets) {
      requireMember(tid, me);
      assertCanSpeak(tid, me);
      last = insertOutboundMessage(me, tid, "template", body, resolvedTitle, silent);
    }
    return last;
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendApprove}：以审批助手机器人向个人用户发审批模板卡片（静默）。
   */
  @Transactional
  public DialogMessageView sendApprove(
      long toUserId, String type, String action, Integer isFinished, String data, String title) {
    AuthContext.requireUserId();
    String cardType = type == null ? "" : type.trim();
    if (toUserId <= 0 || !APPROVE_CARD_TYPES.contains(cardType)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_APPROVE_TYPE);
    }
    UserAccount peer = requireActiveHumanPeer(toUserId);
    JsonNode dataNode = parseJsonObjectOrEmpty(data);
    String resolvedTitle = title == null ? "" : title.trim();
    ObjectNode payload = JSON.createObjectNode();
    payload.put("type", cardType);
    if (action == null || action.isBlank()) {
      payload.putNull("action");
    } else {
      payload.put("action", action.trim());
    }
    payload.put("isFinished", isFinished == null ? 0 : (isFinished != 0 ? 1 : 0));
    payload.set("data", dataNode);
    payload.put("title", resolvedTitle);
    String body = writeJson(payload);
    String preview =
        resolvedTitle.isEmpty() ? "[" + defaultBotNickname("approval-alert") + "]" : resolvedTitle;
    long botUserId = requireBotUserId("approval-alert", defaultBotNickname("approval-alert"), false);
    long dialogId = ensureUserDialog(botUserId, peer.getUserId());
    return insertOutboundMessage(botUserId, dialogId, "template", body, preview, true);
  }

  /**
   * 契约废弃占位：{@code /api/dialog/message/aiGenerate} · {@code webhookMessageToAi} · {@code
   * applied}。
   */
  public Map<String, Object> deprecatedMessageStub() {
    return Map.of("deprecated", true);
  }

  /** 契约 {@code GET /api/dialog/sticker/search}：在线表情搜索。 */
  public Map<String, Object> stickerSearch(String key) {
    AuthContext.requireUserId();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("list", stickerSearch.search(key));
    return out;
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendAiAssistant}：以 AI 助手机器人身份发文本（可 md / 昵称 / 静默）。
   */
  @Transactional
  public DialogMessageView sendAiAssistant(
      Long dialogId,
      Long taskId,
      String text,
      String textType,
      String silence,
      String nickname) {
    long me = AuthContext.requireUserId();
    String bodyText = text == null ? "" : text.trim();
    if (bodyText.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (codePointLen(bodyText) > MAX_AI_ASSISTANT_CHARS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_TOO_LONG);
    }
    String nick = nickname == null ? "" : nickname.trim();
    if (codePointLen(nick) > MAX_AI_NICKNAME_CHARS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_AI_NICKNAME);
    }
    long targetDialogId;
    if (dialogId != null && dialogId > 0) {
      requireMember(dialogId, me);
      targetDialogId = dialogId;
    } else if (taskId != null && taskId > 0) {
      TaskDialogOpenBridge open = taskDialogOpen.getIfAvailable();
      if (open == null) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OPEN_FAILED);
      }
      targetDialogId = open.ensureAccessibleDialog(taskId, me);
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_AI_ASSISTANT_TARGET);
    }
    if (dialogs.findActive(targetDialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    long botUserId =
        requireBotUserId(
            "ai-openai",
            "ChatGPT",
            false);
    if (!dialogs.isMember(targetDialogId, botUserId)) {
      dialogs.insertMember(IdGenerator.nextId(), targetDialogId, botUserId);
    }
    String tt = textType == null ? "md" : textType.trim().toLowerCase(java.util.Locale.ROOT);
    boolean markdown = "md".equals(tt) || "markdown".equals(tt);
    ObjectNode payload = JSON.createObjectNode();
    payload.put("text", bodyText);
    if (markdown) {
      payload.put("type", "md");
    }
    if (!nick.isEmpty()) {
      payload.put("nickname", nick);
    }
    String body = writeJson(payload);
    String preview = cutChars(bodyText, 80);
    return insertOutboundMessage(
        botUserId, targetDialogId, "text", body, preview, isTruthy(silence));
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendLocation}：发送位置消息。
   */
  @Transactional
  public DialogMessageView sendLocation(
      long dialogId,
      String type,
      Double lng,
      Double lat,
      String title,
      Integer distance,
      String address,
      String thumb) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    assertCanSpeak(dialogId, me);
    assertNotHumanToBotDm(dialogId, me);
    String mapType = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
    if (!LOCATION_TYPES.contains(mapType)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_LOCATION_TYPE);
    }
    if (lng == null
        || lat == null
        || lng < -180
        || lng > 180
        || lat < -90
        || lat > 90
        || (lng == 0.0 && lat == 0.0)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_LOCATION_COORDS);
    }
    String name = title == null ? "" : title.trim();
    if (name.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_LOCATION_TITLE);
    }
    ObjectNode payload = JSON.createObjectNode();
    payload.put("type", mapType);
    payload.put("lng", lng);
    payload.put("lat", lat);
    payload.put("title", name);
    payload.put("distance", distance == null ? 0 : Math.max(0, distance));
    payload.put("address", address == null ? "" : address.trim());
    payload.put("thumb", thumb == null ? "" : thumb.trim());
    String body = writeJson(payload);
    return insertOutboundMessage(me, dialogId, "location", body, cutChars(name, 80), false);
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendRecord}：发送语音（data-URL base64）；时长 ≥600ms。
   */
  @Transactional
  public DialogMessageView sendRecord(long dialogId, String base64, Integer duration, Long replyId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    assertCanSpeak(dialogId, me);
    assertNotHumanToBotDm(dialogId, me);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    int ms = duration == null ? 0 : duration;
    if (ms < MIN_RECORD_MS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TOO_SHORT);
    }
    DecodedRecord decoded = decodeRecord64(base64);
    String ym = YearMonth.now(ZoneOffset.UTC).toString().replace("-", "");
    String name = "record_" + md5Hex(decoded.bytes()) + "." + decoded.extension();
    String key = "chat/" + dialogId + "/" + ym + "/" + name;
    ObjectStorage storage = objectStorage.getIfAvailable();
    if (storage == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_STORAGE);
    }
    String url =
        storage.put(
            key,
            new ByteArrayInputStream(decoded.bytes()),
            decoded.bytes().length,
            "audio/" + decoded.extension());
    ObjectNode payload = JSON.createObjectNode();
    payload.put("name", name);
    payload.put("size", decoded.bytes().length);
    payload.put("path", key);
    payload.put("url", url == null || url.isBlank() ? "/" + key : url);
    payload.put("ext", decoded.extension());
    payload.put("duration", ms);
    String body = writeJson(payload);

    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(me);
    m.setType("record");
    m.setBody(body);
    m.setReplyId(replyId == null ? 0L : replyId);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, "[语音]", now);
    dialogs.bumpUnreadExcept(dialogId, me);
    initMessageReads(dialogId, m.getId(), me, now, "record");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    return DialogMessageView.from(m);
  }

  /**
   * 契约 {@code POST /api/dialog/message/convertRecord}：录音转文字（不落消息）；可选翻译。
   *
   * @return 识别或翻译后的文本
   */
  @Transactional
  public String convertRecord(String base64, Integer duration, Long dialogId, String translate) {
    long me = AuthContext.requireUserId();
    int ms = duration == null ? 0 : duration;
    if (ms < MIN_RECORD_MS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TOO_SHORT);
    }
    DecodedRecord decoded = decodeRecord64(base64);
    String ym = YearMonth.now(ZoneOffset.UTC).toString().replace("-", "");
    String name = "record_" + md5Hex(decoded.bytes()) + "." + decoded.extension();
    String key = "chat/tmp/" + me + "/" + ym + "/" + name;
    ObjectStorage storage = objectStorage.getIfAvailable();
    if (storage != null) {
      storage.put(
          key,
          new ByteArrayInputStream(decoded.bytes()),
          decoded.bytes().length,
          "audio/" + decoded.extension());
    }
    String prompt = buildTranscriptionPrompt(me, dialogId);
    AiBotChatService ai = aiBotChat.getIfAvailable();
    if (ai == null || !ai.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TRANSCRIBE);
    }
    String text =
        ai.transcribe(decoded.bytes(), name, "audio/" + decoded.extension(), prompt);
    if (text == null || text.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TRANSCRIBE);
    }
    if (translate == null || translate.isBlank()) {
      return text.trim();
    }
    if (!Messages.isSupportedUserLang(translate)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TRANSLATION_LANG);
    }
    String lang = Messages.toUserLang(translate);
    String targetName = "en-US".equalsIgnoreCase(lang) ? "English" : "Chinese";
    String translated =
        ai.chat(
            "You are a translator. Translate the user message into "
                + targetName
                + ". Return only the translation, no quotes or explanation.",
            text.trim());
    if (translated == null || translated.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TRANSCRIBE);
    }
    return translated.trim();
  }

  /**
   * 契约 {@code GET /api/dialog/message/voiceToText}：已有语音消息转文字并写回 body.text。
   */
  @Transactional
  public DialogMessageView voiceToText(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    if (!"record".equals(message.getType() == null ? "" : message.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TYPE);
    }
    ObjectNode root = parseRecordBody(message.getBody());
    String existingText = textOrEmpty(root.get("text"));
    if (!existingText.isBlank()) {
      ArrayNode userIds = ensureTextUserIds(root);
      boolean found = false;
      for (JsonNode n : userIds) {
        if (n != null && n.asLong() == me) {
          found = true;
          break;
        }
      }
      if (!found) {
        userIds.add(me);
        LocalDateTime now = LocalDateTime.now();
        dialogs.updateMessageBody(messageId, writeJson(root), now);
        message.setBody(writeJson(root));
        message.setUpdatedAt(now);
      }
      return DialogMessageView.from(message);
    }
    String path = textOrEmpty(root.get("path"));
    if (path.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    ObjectStorage storage = objectStorage.getIfAvailable();
    if (storage == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_STORAGE);
    }
    byte[] audio;
    try (InputStream in = storage.open(path)) {
      audio = in.readAllBytes();
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    if (audio.length == 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    String ext = textOrEmpty(root.get("ext"));
    if (ext.isBlank()) {
      ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "mp3";
    }
    String name = textOrEmpty(root.get("name"));
    if (name.isBlank()) {
      name = "record." + ext;
    }
    String prompt = buildTranscriptionPrompt(me, message.getDialogId());
    AiBotChatService ai = aiBotChat.getIfAvailable();
    if (ai == null || !ai.available()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TRANSCRIBE);
    }
    String text = ai.transcribe(audio, name, "audio/" + ext, prompt);
    if (text == null || text.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_TRANSCRIBE);
    }
    root.put("text", text.trim());
    ArrayNode userIds = ensureTextUserIds(root);
    userIds.removeAll();
    userIds.add(me);
    LocalDateTime now = LocalDateTime.now();
    String body = writeJson(root);
    dialogs.updateMessageBody(messageId, body, now);
    message.setBody(body);
    message.setUpdatedAt(now);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", message.getDialogId());
    data.put("message", DialogMessageView.from(message));
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_UPDATE,
        dialogs.listMemberUserIds(message.getDialogId()),
        data);
    return DialogMessageView.from(message);
  }

  private String buildTranscriptionPrompt(long userId, Long dialogId) {
    List<String> parts = new ArrayList<>();
    Optional<UserAccount> me = users.findByUserId(userId);
    String lang = me.map(UserAccount::getLang).orElse("");
    if (lang != null && lang.toLowerCase(java.util.Locale.ROOT).startsWith("zh")) {
      parts.add("如果识别到中文，优先使用简体中文输出");
    }
    if (dialogId != null && dialogId > 0 && dialogs.isMember(dialogId, userId)) {
      List<String> ctx = new ArrayList<>();
      for (DialogMessage m :
          dialogs.listMessages(dialogId, null, 5, dialogs.findUserSessionKey(dialogId, userId))) {
        if (!"text".equals(m.getType() == null ? "" : m.getType())) {
          continue;
        }
        String snippet = extractTranslationText(m.getBody());
        if (!snippet.isBlank()) {
          ctx.add(cutChars(snippet, 100));
        }
      }
      Collections.reverse(ctx);
      if (!ctx.isEmpty()) {
        parts.add("对话上下文：" + String.join("；", ctx) + "。");
      }
    }
    return parts.isEmpty() ? null : String.join("\n\n", parts);
  }

  private static ObjectNode parseRecordBody(String body) {
    if (body == null || body.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    try {
      JsonNode root = JSON.readTree(body);
      if (root != null && root.isObject()) {
        return (ObjectNode) root;
      }
    } catch (Exception ignored) {
      // fall through
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
  }

  private static ArrayNode ensureTextUserIds(ObjectNode root) {
    JsonNode existing = root.get("textUserId");
    if (existing != null && existing.isArray()) {
      return (ArrayNode) existing;
    }
    ArrayNode arr = root.putArray("textUserId");
    return arr;
  }

  private static DecodedRecord decodeRecord64(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty() || !value.startsWith("data:")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    int comma = value.indexOf(',');
    if (comma < 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    String meta = value.substring(5, comma).toLowerCase(java.util.Locale.ROOT);
    String b64 = value.substring(comma + 1);
    String ext;
    if (meta.contains("audio/mp3") || meta.contains("audio/mpeg")) {
      ext = "mp3";
    } else if (meta.contains("audio/wav") || meta.contains("audio/x-wav") || meta.contains("audio/wave")) {
      ext = "wav";
    } else {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    byte[] bytes;
    try {
      bytes = java.util.Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    if (bytes.length == 0 || bytes.length > MAX_RECORD_BYTES) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_RECORD_INVALID);
    }
    return new DecodedRecord(ext, bytes);
  }

  private static String md5Hex(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (Exception e) {
      return UUID.randomUUID().toString().replace("-", "");
    }
  }

  private record DecodedRecord(String extension, byte[] bytes) {}

  private static List<Map<String, String>> parseTemplateContent(String content) {
    if (content == null || content.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_EMPTY);
    }
    JsonNode root;
    try {
      root = JSON.readTree(content);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_EMPTY);
    }
    if (!root.isArray() || root.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_EMPTY);
    }
    List<Map<String, String>> items = new ArrayList<>();
    for (JsonNode node : root) {
      if (node == null || !node.isObject()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_EMPTY);
      }
      String itemContent = textOrEmpty(node.get("content"));
      String style = textOrEmpty(node.get("style"));
      int contentLen = codePointLen(itemContent);
      if (contentLen < 1 || contentLen > MAX_TEMPLATE_ITEM_CHARS) {
        throw new BusinessException(
            ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_CONTENT, MAX_TEMPLATE_ITEM_CHARS);
      }
      if (codePointLen(style) > MAX_TEMPLATE_ITEM_CHARS) {
        throw new BusinessException(
            ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_STYLE, MAX_TEMPLATE_ITEM_CHARS);
      }
      Map<String, String> row = new LinkedHashMap<>();
      row.put("content", itemContent);
      row.put("style", style);
      items.add(row);
    }
    return items;
  }

  private static JsonNode parseJsonObjectOrEmpty(String raw) {
    if (raw == null || raw.isBlank()) {
      return JSON.createObjectNode();
    }
    try {
      JsonNode node = JSON.readTree(raw);
      return node != null && node.isObject() ? node : JSON.createObjectNode();
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_APPROVE_TYPE);
    }
  }

  private static String writeJson(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEMPLATE_EMPTY);
    }
  }

  private static String textOrEmpty(JsonNode node) {
    if (node == null || node.isNull()) {
      return "";
    }
    return node.asString("").trim();
  }

  private static int codePointLen(String s) {
    return s == null ? 0 : s.codePointCount(0, s.length());
  }

  private static String cutChars(String s, int max) {
    if (s == null || max <= 0) {
      return "";
    }
    if (codePointLen(s) <= max) {
      return s;
    }
    return s.substring(0, s.offsetByCodePoints(0, max));
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendAnon}：经匿名机器人向个人用户发文本。
   */
  @Transactional
  public DialogMessageView sendAnon(long peerUserId, String text) {
    AuthContext.requireUserId();
    SystemGeneralSettingService settings = systemSettings.getIfAvailable();
    if (settings != null && !settings.isAnonMessageOpen()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_ANON_DISABLED);
    }
    UserAccount peer = requireActiveHumanPeer(peerUserId);
    String body = text == null ? "" : text.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (body.length() > MAX_BOT_TEXT_CHARS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_TOO_LONG);
    }
    long botUserId = requireBotUserId("anon-msg", "匿名消息", false);
    long dialogId = ensureUserDialog(botUserId, peer.getUserId());
    return insertOutboundMessage(botUserId, dialogId, "text", body, body, false);
  }

  /**
   * 契约 {@code POST /api/dialog/message/sendBot}：以系统/自定义机器人身份向个人用户发 markdown 文本。
   */
  @Transactional
  public DialogMessageView sendBot(
      long peerUserId, String text, String botType, String botName, String silence) {
    AuthContext.requireUserId();
    UserAccount peer = requireActiveHumanPeer(peerUserId);
    String body = text == null ? "" : text.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (body.length() > MAX_BOT_TEXT_CHARS) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_TOO_LONG);
    }
    String prefix = resolveSendBotPrefix(botType, botName);
    String display =
        botName != null && !botName.isBlank()
            ? botName.trim()
            : defaultBotNickname(prefix);
    long botUserId = requireBotUserId(prefix, display, prefix.startsWith("user-auto-"));
    long dialogId = ensureUserDialog(botUserId, peer.getUserId());
    return insertOutboundMessage(botUserId, dialogId, "text", body, body, isTruthy(silence));
  }

  private UserAccount requireActiveHumanPeer(long peerUserId) {
    if (peerUserId <= 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_PEER_REQUIRED);
    }
    UserAccount peer =
        users
            .findByUserId(peerUserId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND));
    if (peer.getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_ANON_PEER);
    }
    if (peer.getDisableAt() != null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_PEER_DISABLED);
    }
    return peer;
  }

  private static String resolveSendBotPrefix(String botType, String botName) {
    String raw = botType == null || botType.isBlank() ? "system-msg" : botType.trim();
    if ("check-in".equalsIgnoreCase(raw) || "checkin".equalsIgnoreCase(raw)) {
      return "attendance";
    }
    if (SYSTEM_BOT_PREFIXES.contains(raw)) {
      return raw;
    }
    if (raw.length() < 6 || raw.length() > 20 || !raw.matches("[A-Za-z0-9_-]+")) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_BOT_TYPE_INVALID);
    }
    if (botName != null && !botName.isBlank()) {
      String n = botName.trim();
      if (n.length() < 2 || n.length() > 20) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NAME_INVALID);
      }
    }
    return "user-auto-" + raw;
  }

  private static String defaultBotNickname(String prefix) {
    return switch (prefix) {
      case "system-msg" -> "系统消息";
      case "task-alert" -> "任务提醒";
      case "todo-alert" -> "待办提醒";
      case "attendance" -> "签到打卡";
      case "anon-msg" -> "匿名消息";
      case "approval-alert" -> "审批";
      case "meeting-alert" -> "会议通知";
      case "okr-alert" -> "OKR 提醒";
      case "bot-manager" -> "机器人管理";
      default -> prefix;
    };
  }

  private long requireBotUserId(String prefix, String nickname, boolean createIfMissing) {
    String email = prefix + "@bot.system";
    Optional<UserAccount> existing = users.findByEmail(email);
    if (existing.isPresent() && existing.get().getIsBot() == 1) {
      return existing.get().getUserId();
    }
    if (!createIfMissing) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NOT_FOUND);
    }
    PasswordEncoder encoder = passwordEncoder.getIfAvailable();
    if (encoder == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NOT_FOUND);
    }
    UserAccount bot = new UserAccount();
    bot.setUserId(IdGenerator.nextId());
    bot.setEmail(email);
    bot.setNickname(nickname == null || nickname.isBlank() ? prefix : nickname.trim());
    bot.setUserImage("");
    bot.setIdentity("[]");
    bot.setPassword(encoder.encode(UUID.randomUUID().toString()));
    bot.setIsBot(1);
    users.insert(bot);
    return bot.getUserId();
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
    d.setUnreadCount(0);
    d.setIsTop(0);
    dialogs.insertDialog(d);
    dialogs.insertMember(IdGenerator.nextId(), d.getId(), userIdA);
    dialogs.insertMember(IdGenerator.nextId(), d.getId(), userIdB);
    return d.getId();
  }

  private DialogMessageView insertOutboundMessage(
      long senderId, long dialogId, String type, String body, String preview, boolean silence) {
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(senderId);
    m.setType(type);
    m.setBody(body);
    m.setReplyId(0L);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    String p = preview == null ? "" : preview;
    if (p.length() > 80) {
      p = p.substring(0, 80);
    }
    dialogs.touchDialog(dialogId, p, now);
    if (!silence) {
      dialogs.bumpUnreadExcept(dialogId, senderId);
    }
    initMessageReads(dialogId, m.getId(), senderId, now);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    if (silence) {
      data.put("silence", true);
    }
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    return DialogMessageView.from(m);
  }

  private static boolean isTruthy(String raw) {
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String v = raw.trim().toLowerCase();
    return "1".equals(v) || "true".equals(v) || "yes".equals(v);
  }

  /** 机器人身份发文本（Webhook 回调回复；跳过鉴权上下文）。 */
  @Transactional
  public DialogMessageView sendTextAsBot(long botUserId, long dialogId, String text) {
    if (!dialogs.isMember(dialogId, botUserId)) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_DENIED);
    }
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    Optional<UserAccount> bot = users.findByUserId(botUserId);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NOT_FOUND);
    }
    String body = text == null ? "" : text.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (body.length() > 5000) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_TOO_LONG);
    }

    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(botUserId);
    m.setType("text");
    m.setBody(body);
    m.setReplyId(0L);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    dialogs.touchDialog(dialogId, body, now);
    dialogs.bumpUnreadExcept(dialogId, botUserId);
    initMessageReads(dialogId, m.getId(), botUserId, now);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    publishSearchIndex(
        SearchIndexEvent.ACTION_UPSERT,
        SearchIndexEvent.TYPE_MESSAGE,
        m.getId(),
        botUserId,
        0L,
        body.length() > 80 ? body.substring(0, 80) : body,
        body);
    recordTaskMentions(dialogId, m.getId(), botUserId, body);
    return DialogMessageView.from(m);
  }

  /**
   * AI 建议 Markdown（可更长）；调用方须保证 bot 已是会话成员。
   */
  @Transactional
  public DialogMessageView sendMarkdownAsBot(long botUserId, long dialogId, String markdown) {
    if (!dialogs.isMember(dialogId, botUserId)) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_DENIED);
    }
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    Optional<UserAccount> bot = users.findByUserId(botUserId);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BOT_NOT_FOUND);
    }
    String body = markdown == null ? "" : markdown.trim();
    if (body.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_TEXT_EMPTY);
    }
    if (body.length() > 20000) {
      body = body.substring(0, 20000);
    }

    LocalDateTime now = LocalDateTime.now();
    DialogMessage m = new DialogMessage();
    m.setId(IdGenerator.nextId());
    m.setDialogId(dialogId);
    m.setUserId(botUserId);
    m.setType("text");
    m.setBody(body);
    m.setReplyId(0L);
    m.setCreatedAt(now);
    dialogs.insertMessage(m);
    String preview = body.length() > 80 ? body.substring(0, 80) : body;
    dialogs.touchDialog(dialogId, preview, now);
    dialogs.bumpUnreadExcept(dialogId, botUserId);
    initMessageReads(dialogId, m.getId(), botUserId, now);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(m));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    return DialogMessageView.from(m);
  }

  /** 更新消息正文并扇出（AI 卡片 status / message_id 回填）。 */
  @Transactional
  public DialogMessageView updateMessageAsBot(long botUserId, long dialogId, long messageId, String markdown) {
    DialogMessage existing =
        dialogs
            .findMessage(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    if (existing.getDialogId() != dialogId) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND);
    }
    String body = markdown == null ? "" : markdown.trim();
    if (body.length() > 20000) {
      body = body.substring(0, 20000);
    }
    LocalDateTime now = LocalDateTime.now();
    dialogs.updateMessageBody(messageId, body, now);
    existing.setBody(body);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", dialogId);
    data.put("message", DialogMessageView.from(existing));
    publishFanout(RealtimeEventTypes.DIALOG_MESSAGE, dialogs.listMemberUserIds(dialogId), data);
    return DialogMessageView.from(existing);
  }

  @Transactional
  public DialogView groupAdd(String chatName, String userIds, String avatar) {
    long me = AuthContext.requireUserId();
    List<Long> members = parseUserIds(userIds);
    if (!members.contains(me)) {
      members.add(0, me);
    }
    members = members.stream().distinct().toList();
    if (members.size() < 2) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_MEMBERS);
    }
    for (Long userId : members) {
      if (!users.existsByUserId(userId)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND_ID, userId);
      }
      assertNotBotUser(userId);
    }
    String name = chatName == null ? "" : chatName.trim();
    if (name.isEmpty()) {
      name = "Group";
    }
    if (name.length() > 100) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_NAME);
    }

    LocalDateTime now = LocalDateTime.now();
    Dialog d = new Dialog();
    d.setId(IdGenerator.nextId());
    d.setType("group");
    d.setGroupType("user");
    d.setName(name);
    d.setAvatar(avatar == null ? "" : avatar.trim());
    d.setOwnerId(me);
    d.setLinkId(0L);
    d.setLastMessage("");
    d.setLastAt(now);
    d.setCreatedAt(now);
    d.setUnreadCount(0);
    d.setIsTop(0);
    dialogs.insertDialog(d);
    for (Long userId : members) {
      dialogs.insertMember(IdGenerator.nextId(), d.getId(), userId);
    }
    return DialogView.from(d);
  }

  @Transactional
  public DialogView groupEdit(long dialogId, String chatName, String avatar) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    requireManage(d, me);
    if (chatName != null) {
      String name = chatName.trim();
      if (name.isEmpty() || name.length() > 100) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_NAME);
      }
      d.setName(name);
    }
    if (avatar != null) {
      d.setAvatar(avatar.trim());
    }
    dialogs.updateDialogMeta(dialogId, d.getName(), d.getAvatar());
    return one(dialogId);
  }

  @Transactional
  public List<Long> groupAddUser(long dialogId, String userIds) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    requireManage(d, me);
    List<Long> ids = parseUserIds(userIds);
    if (ids.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_MEMBERS);
    }
    List<Long> joined = new ArrayList<>();
    for (Long userId : ids) {
      if (!users.existsByUserId(userId)) {
        throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND_ID, userId);
      }
      assertNotBotUser(userId);
      if (!dialogs.isMember(dialogId, userId)) {
        dialogs.insertMember(IdGenerator.nextId(), dialogId, userId);
        joined.add(userId);
      }
    }
    for (Long userId : joined) {
      userBotWebhook.afterMemberChange(d, UserBotWebhookEvent.EVENT_MEMBER_JOIN, userId, me);
    }
    return dialogs.listMemberUserIds(dialogId);
  }

  @Transactional
  public List<Long> groupDelUser(long dialogId, String userIds) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    List<Long> ids = parseUserIds(userIds);
    boolean selfExit = ids.size() == 1 && ids.get(0) == me;
    List<Long> left = new ArrayList<>();
    if (selfExit) {
      requireMember(dialogId, me);
      if (d.getOwnerId() == me) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_OWNER_EXIT);
      }
      dialogs.deleteMember(dialogId, me);
      left.add(me);
    } else {
      requireManage(d, me);
      for (Long userId : ids) {
        if (userId == d.getOwnerId()) {
          throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_GROUP_OWNER_ONLY);
        }
        if (userId != me && dialogs.isMember(dialogId, userId)) {
          dialogs.deleteMember(dialogId, userId);
          left.add(userId);
        }
      }
    }
    for (Long userId : left) {
      userBotWebhook.afterMemberChange(d, UserBotWebhookEvent.EVENT_MEMBER_LEAVE, userId, me);
    }
    return dialogs.listMemberUserIds(dialogId);
  }

  @Transactional
  public DialogView groupTransfer(long dialogId, long userId) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    if (d.getOwnerId() != me) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_GROUP_OWNER_ONLY);
    }
    requireMember(dialogId, userId);
    if (userId == me) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_OPEN_FAILED);
    }
    dialogs.updateOwner(dialogId, userId);
    dialogs.setDeputy(dialogId, userId, false);
    return one(dialogId);
  }

  @Transactional
  public List<Long> groupAddDeputy(long dialogId, long userId) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    if (d.getOwnerId() != me) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_GROUP_OWNER_ONLY);
    }
    requireMember(dialogId, userId);
    if (userId == d.getOwnerId()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_OWNER_ONLY);
    }
    dialogs.setDeputy(dialogId, userId, true);
    return dialogs.listDeputyUserIds(dialogId);
  }

  @Transactional
  public List<Long> groupDelDeputy(long dialogId, long userId) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    if (d.getOwnerId() != me) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_GROUP_OWNER_ONLY);
    }
    requireMember(dialogId, userId);
    dialogs.setDeputy(dialogId, userId, false);
    return dialogs.listDeputyUserIds(dialogId);
  }

  /** 契约 {@code GET /api/dialog/group/deputies}：普通群管理员 userId 列表。 */
  public List<Long> groupDeputies(long dialogId) {
    long me = AuthContext.requireUserId();
    requireUserGroup(dialogId);
    requireMember(dialogId, me);
    return dialogs.listDeputyUserIds(dialogId);
  }

  @Transactional
  public void groupDisband(long dialogId) {
    long me = AuthContext.requireUserId();
    Dialog d = requireUserGroup(dialogId);
    if (d.getOwnerId() != me) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_GROUP_OWNER_ONLY);
    }
    dialogs.softDeleteDialog(dialogId);
  }

  /**
   * 系统管理员按群名搜索普通个人群（建部门等）；返回 {@code {list:[...]}}，最多 20 条。
   * {@code key} 可空：空则按最近活跃返回。
   */
  public Map<String, Object> groupSearchUser(String key) {
    adminGuard.requireAdmin();
    String q = key == null ? "" : key.trim();
    if (q.length() > 64) {
      q = q.substring(0, 64);
    }
    String searchKey = q.isEmpty() ? "" : escapeLike(q);
    List<DialogView> list =
        dialogs.searchUserGroups(searchKey, 20).stream().map(DialogView::from).toList();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("list", list);
    return data;
  }

  /**
   * 当前用户的普通个人群；{@code targetUserId} 有值时为与对方的共同群。
   * {@code onlyCount=yes} → {@code {total}}；否则分页 {@code {list,page,pageSize,total}}。
   */
  public Map<String, Object> commonList(
      Long targetUserId, String onlyCount, Integer page, Integer pageSize) {
    long me = AuthContext.requireUserId();
    Long target = targetUserId == null || targetUserId <= 0 ? null : targetUserId;
    if (target != null && !users.existsByUserId(target)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.USER_NOT_FOUND_ID, target);
    }
    long total = dialogs.countCommonUserGroups(me, target);
    boolean countOnly = onlyCount != null && "yes".equalsIgnoreCase(onlyCount.trim());
    if (countOnly) {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("total", total);
      return data;
    }
    int p = page == null || page < 1 ? 1 : page;
    int size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
    List<DialogView> list =
        dialogs.pageCommonUserGroups(me, target, (p - 1) * size, size).stream()
            .map(DialogView::from)
            .toList();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("list", list);
    data.put("page", p);
    data.put("pageSize", size);
    data.put("total", total);
    return data;
  }

  @Transactional
  public DialogView top(long dialogId, boolean top) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    dialogs.setIsTop(dialogId, me, top);
    return one(dialogId);
  }

  @Transactional
  public void hide(long dialogId, boolean isHidden) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    dialogs.setIsHidden(dialogId, me, isHidden);
  }

  /** 读取当前用户在会话上的个人配置（isMuted/isTop/isHidden）。 */
  public DialogConfigView config(long dialogId) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    if (dialogs.findActive(dialogId).isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND);
    }
    return toConfigView(dialogId, me);
  }

  /**
   * 保存会话配置。支持 {@code isMuted}（个人免打扰）、{@code tag}、{@code isChatMuted}（群禁言，仅群主/管理员）。
   */
  @Transactional
  public DialogConfigView configSave(
      long dialogId, Integer isMuted, String tag, Integer isChatMuted) {
    long me = AuthContext.requireUserId();
    requireMember(dialogId, me);
    Dialog d =
        dialogs
            .findActive(dialogId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
    if (isMuted == null && tag == null && isChatMuted == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_CONFIG_INVALID);
    }
    if (isMuted != null) {
      if (isMuted != 0 && isMuted != 1) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_CONFIG_INVALID);
      }
      if (isMuted == 1) {
        requireMuteAllowed(d);
      }
      dialogs.setIsMuted(dialogId, me, isMuted == 1);
    }
    if (tag != null) {
      String t = tag.trim();
      if (t.length() > 64) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_CONFIG_INVALID);
      }
      dialogs.setTag(dialogId, me, t);
    }
    if (isChatMuted != null) {
      if (isChatMuted != 0 && isChatMuted != 1) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_CONFIG_INVALID);
      }
      requireUserGroup(dialogId);
      requireManage(d, me);
      dialogs.upsertChatMuted(IdGenerator.nextId(), dialogId, isChatMuted == 1, LocalDateTime.now());
    }
    return toConfigView(dialogId, me);
  }

  /**
   * 消息免打扰开关（契约 message/silence）；{@code isSilent=1} 开启，{@code 0} 关闭。
   * 与 {@link #configSave} 写同一 {@code bluedock_dialog_users.is_muted}。
   */
  @Transactional
  public DialogConfigView messageSilence(long dialogId, int isSilent) {
    return configSave(dialogId, isSilent != 0 ? 1 : 0, null, null);
  }

  private DialogConfigView toConfigView(long dialogId, long userId) {
    Map<String, Object> flags =
        dialogs
            .findUserFlags(dialogId, userId)
            .orElse(Map.of("isTop", 0, "isHidden", 0, "isMuted", 0, "tag", "", "color", ""));
    return new DialogConfigView(
        dialogId,
        ((Number) flags.get("isMuted")).intValue(),
        ((Number) flags.get("isTop")).intValue(),
        ((Number) flags.get("isHidden")).intValue(),
        String.valueOf(flags.getOrDefault("tag", "")),
        dialogs.findChatMuted(dialogId),
        String.valueOf(flags.getOrDefault("color", "")));
  }

  /**
   * 发言守卫：系统管理员豁免；群主/管理员豁免会话级禁言与普通群全局禁言；
   * 系统群不受 userGroupChatMute 约束（全员群看 allGroupMute）。
   */
  private void assertCanSpeak(long dialogId, long userId) {
    if (adminGuard.isAdmin(userId)) {
      return;
    }
    Dialog d =
        dialogs
            .findActive(dialogId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
    boolean manager =
        "group".equals(d.getType())
            && (d.getOwnerId() == userId || dialogs.isDeputy(d.getId(), userId));
    if (dialogs.findChatMuted(dialogId) == 1 && !manager) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_CHAT_MUTED);
    }
    SystemGeneralSettingService settings = systemSettings.getIfAvailable();
    if (settings == null) {
      return;
    }
    if ("user".equals(d.getType()) && !settings.isUserPrivateChatMuteOpen()) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_CHAT_MUTED);
    }
    if ("group".equals(d.getType())) {
      String gt = d.getGroupType() == null ? "" : d.getGroupType();
      if ("user".equals(gt) && !settings.isUserGroupChatMuteOpen() && !manager) {
        throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_CHAT_MUTED);
      }
      if ("all".equals(gt) && settings.isAllGroupMuteOpen()) {
        throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_CHAT_MUTED);
      }
    }
  }

  private static void requireMuteAllowed(Dialog d) {
    if (!"group".equals(d.getType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_MUTE_DENIED);
    }
    String gt = d.getGroupType() == null ? "" : d.getGroupType();
    if (!"user".equals(gt)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_MUTE_DENIED);
    }
  }

  private static String escapeLike(String raw) {
    return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  @Transactional
  public void withdraw(long messageId) {
    long me = AuthContext.requireUserId();
    DialogMessage message =
        dialogs
            .findMessage(messageId)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_MESSAGE_NOT_FOUND));
    requireMember(message.getDialogId(), me);
    if (message.getUserId() != me) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_MESSAGE_WITHDRAW_DENIED);
    }
    assertWithdrawWithinLimit(message, me);
    dialogs.softDeleteMessage(messageId);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("dialogId", message.getDialogId());
    data.put("messageId", messageId);
    publishFanout(
        RealtimeEventTypes.DIALOG_MESSAGE_WITHDRAW,
        dialogs.listMemberUserIds(message.getDialogId()),
        data);
    publishSearchIndex(
        SearchIndexEvent.ACTION_DELETE,
        SearchIndexEvent.TYPE_MESSAGE,
        messageId,
        me,
        0L,
        "",
        "");
  }

  /**
   * 撤回时限：系统 {@code messageRecallLimit}（分钟）；0/未配置=不限制。自聊与机器人作者消息豁免。
   */
  private void assertWithdrawWithinLimit(DialogMessage message, long me) {
    if (isWithdrawUnlimited(message, me)) {
      return;
    }
    SystemGeneralSettingService settings = systemSettings.getIfAvailable();
    int limit = settings == null ? 0 : settings.messageRecallLimitMinutes();
    if (limit <= 0 || message.getCreatedAt() == null) {
      return;
    }
    long elapsed = ChronoUnit.MINUTES.between(message.getCreatedAt(), LocalDateTime.now());
    if (elapsed >= limit) {
      throw new BusinessException(
          ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_MESSAGE_WITHDRAW_EXPIRED, limit);
    }
  }

  private boolean isWithdrawUnlimited(DialogMessage message, long me) {
    Optional<UserAccount> author = users.findByUserId(message.getUserId());
    if (author.isPresent() && author.get().getIsBot() == 1) {
      return true;
    }
    Dialog d = dialogs.findActive(message.getDialogId()).orElse(null);
    if (d != null && "user".equals(d.getType())) {
      List<Long> members = dialogs.listMemberUserIds(d.getId());
      if (members.isEmpty()) {
        return false;
      }
      boolean onlyMe = members.stream().allMatch(memberUserId -> memberUserId != null && memberUserId == me);
      if (onlyMe) {
        return true;
      }
      if (members.size() == 1) {
        return true;
      }
    }
    return false;
  }

  private void recordTaskMentions(long dialogId, long messageId, long userId, String body) {
    TaskMentionBridge bridge = taskMentions.getIfAvailable();
    if (bridge != null) {
      bridge.recordMentionsFromMessage(dialogId, messageId, userId, body);
    }
  }

  /**
   * 解析用户 @ / @所有人，对仍在群内的非机器人成员累加 mention 未读。
   * 单聊不额外累计（会话本身即直达）。
   *
   * @return 实际命中的 userId 列表
   */
  private List<Long> recordUserMentions(long dialogId, long messageId, long senderId, String body) {
    Dialog dialog = dialogs.findActive(dialogId).orElse(null);
    if (dialog == null || "user".equals(dialog.getType())) {
      return List.of();
    }
    DialogMentionParser.Result parsed = DialogMentionParser.parse(body);
    Set<Long> targets = new HashSet<>();
    List<Long> members = dialogs.listMemberUserIds(dialogId);
    Set<Long> memberSet = new HashSet<>(members);
    if (parsed.all()) {
      targets.addAll(members);
    }
    for (Long userId : parsed.userIds()) {
      if (memberSet.contains(userId)) {
        targets.add(userId);
      }
    }
    targets.remove(senderId);
    List<Long> hit = new ArrayList<>();
    for (Long userId : targets) {
      if (userId == null || userId <= 0) {
        continue;
      }
      Optional<UserAccount> u = users.findByUserId(userId);
      if (u.isPresent() && u.get().getIsBot() == 1) {
        continue;
      }
      dialogs.bumpMention(dialogId, userId, messageId);
      hit.add(userId);
    }
    return hit;
  }

  private void publishSearchIndex(
      String action, String docType, long refId, long userId, long projectId, String title, String content) {
    SearchIndexEvent event =
        new SearchIndexEvent(
            UUID.randomUUID().toString().replace("-", ""),
            action,
            docType,
            refId,
            userId,
            projectId,
            title,
            content);
    searchIndex.publish(event);
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

  private Dialog requireUserGroup(long dialogId) {
    Dialog d =
        dialogs
            .findActive(dialogId)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.DIALOG_NOT_FOUND));
    if (!"group".equals(d.getType()) || !"user".equals(d.getGroupType())) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_GROUP_NOT_USER);
    }
    return d;
  }

  private void requireManage(Dialog d, long userId) {
    requireMember(d.getId(), userId);
    if (d.getOwnerId() != userId && !dialogs.isDeputy(d.getId(), userId)) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_GROUP_MANAGE);
    }
  }

  private void requireMember(long dialogId, long userId) {
    Dialog d = dialogs.findActive(dialogId).orElse(null);
    if (d != null && !allowTaskDialog(d, userId)) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_DENIED);
    }
    if (!dialogs.isMember(dialogId, userId)) {
      throw new BusinessException(ErrorCodes.DIALOG_DENIED, I18nKeys.DIALOG_DENIED);
    }
  }

  private static boolean hasIdentityTag(String identity, String tag) {
    if (identity == null || identity.isBlank() || tag == null || tag.isBlank()) {
      return false;
    }
    String needle = "\"" + tag + "\"";
    return identity.contains(needle);
  }

  private static String displayNickname(UserAccount user) {
    if (user == null) {
      return "";
    }
    if (user.getNickname() != null && !user.getNickname().isBlank()) {
      return user.getNickname().trim();
    }
    if (user.getEmail() != null && !user.getEmail().isBlank()) {
      return user.getEmail().trim();
    }
    return String.valueOf(user.getUserId());
  }

  private static List<long[]> parseLatestDialogs(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = JSON.readTree(raw.trim());
      if (!root.isArray()) {
        return List.of();
      }
      List<long[]> out = new ArrayList<>();
      for (JsonNode node : root) {
        if (node == null || !node.isObject()) {
          continue;
        }
        long id = 0L;
        if (node.has("dialogId")) {
          id = node.get("dialogId").asLong(0L);
        } else if (node.has("id")) {
          id = node.get("id").asLong(0L);
        }
        long latestId = 0L;
        if (node.has("latestId")) {
          latestId = node.get("latestId").asLong(0L);
        } else if (node.has("latest_id")) {
          latestId = node.get("latest_id").asLong(0L);
        }
        if (id > 0) {
          out.add(new long[] {id, latestId});
        }
      }
      return out;
    } catch (Exception ex) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_LATEST_INVALID);
    }
  }

  private static long extractFileId(String body) {
    if (body == null || body.isBlank()) {
      return 0L;
    }
    try {
      JsonNode node = JSON.readTree(body);
      if (node.has("fileId") && node.get("fileId").canConvertToLong()) {
        return node.get("fileId").asLong(0L);
      }
    } catch (Exception ignored) {
      // fall through
    }
    return 0L;
  }

  private static List<DialogMessageView> parseMergeItems(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = JSON.readTree(body);
      JsonNode items = root.get("items");
      if (items == null || !items.isArray()) {
        return List.of();
      }
      List<DialogMessageView> out = new ArrayList<>();
      for (JsonNode item : items) {
        if (item == null || !item.isObject()) {
          continue;
        }
        long messageId = item.has("messageId") ? item.get("messageId").asLong(0L) : 0L;
        long userId = item.has("userId") ? item.get("userId").asLong(0L) : 0L;
        String type = item.has("type") ? item.get("type").asString("") : "";
        String itemBody = "";
        if (item.has("body")) {
          JsonNode b = item.get("body");
          itemBody = b.isTextual() ? b.asString("") : b.toString();
        }
        out.add(new DialogMessageView(messageId, 0L, userId, type, itemBody, 0L, 0L, null));
      }
      return out;
    } catch (Exception ex) {
      return List.of();
    }
  }

  /** 真人不可向机器人单聊发消息（可打开会话查看推送）。 */
  private void assertNotHumanToBotDm(long dialogId, long senderId) {
    Optional<UserAccount> sender = users.findByUserId(senderId);
    if (sender.isPresent() && sender.get().getIsBot() == 1) {
      return;
    }
    Dialog d = dialogs.findActive(dialogId).orElse(null);
    if (d == null || !"user".equals(d.getType())) {
      return;
    }
    for (Long userId : dialogs.listMemberUserIds(dialogId)) {
      if (userId == null || userId == senderId) {
        continue;
      }
      Optional<UserAccount> peer = users.findByUserId(userId);
      if (peer.isPresent() && peer.get().getIsBot() == 1) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_BOT_DM_DENIED);
      }
    }
  }

  private void assertNotBotUser(long userId) {
    Optional<UserAccount> u = users.findByUserId(userId);
    if (u.isPresent() && u.get().getIsBot() == 1) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_BOT_GROUP_DENIED);
    }
  }

  /** 任务群：须对挂接任务可见；其他会话恒 true。 */
  private boolean allowTaskDialog(Dialog d, long userId) {
    if (d == null || !"task".equals(d.getGroupType()) || d.getLinkId() <= 0) {
      return true;
    }
    TaskDialogAccessBridge bridge = taskDialogAccess.getIfAvailable();
    if (bridge == null) {
      return true;
    }
    return bridge.canAccessTaskDialog(d.getLinkId(), userId);
  }

  private static List<Long> parseUserIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return new ArrayList<>();
    }
    List<Long> ids = new ArrayList<>();
    for (String part : raw.split("[,，\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        ids.add(Long.parseLong(part.trim()));
      } catch (NumberFormatException ex) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.PROJECT_USER_ID_INVALID, part);
      }
    }
    return ids;
  }
}
