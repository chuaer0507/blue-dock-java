package com.bluedock.messenger.web;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.model.ResultModel;
import com.bluedock.messenger.service.DialogService;
import com.bluedock.messenger.web.dto.DialogConfigView;
import com.bluedock.messenger.web.dto.DialogMessageDownload;
import com.bluedock.messenger.web.dto.DialogMessageEmojiView;
import com.bluedock.messenger.web.dto.DialogMessageReadListView;
import com.bluedock.messenger.web.dto.DialogMessageTagView;
import com.bluedock.messenger.web.dto.DialogMessageTranslationView;
import com.bluedock.messenger.web.dto.DialogMessageTodoView;
import com.bluedock.messenger.web.dto.DialogMessageView;
import com.bluedock.messenger.web.dto.DialogMergeDetailView;
import com.bluedock.messenger.web.dto.DialogSessionView;
import com.bluedock.messenger.web.dto.DialogTelephoneView;
import com.bluedock.messenger.web.dto.DialogUnreadItemView;
import com.bluedock.messenger.web.dto.DialogView;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/dialog")
public class DialogController {
  private final DialogService dialogs;

  public DialogController(DialogService dialogs) {
    this.dialogs = dialogs;
  }

  @GetMapping("/lists")
  public ResultModel<List<DialogView>> lists() {
    return ResultModel.ok(dialogs.lists());
  }

  @GetMapping("/beyond")
  public ResultModel<List<DialogView>> beyond() {
    return ResultModel.ok(dialogs.beyond());
  }

  @GetMapping("/search")
  public ResultModel<List<DialogView>> search(
      @RequestParam String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(dialogs.search(key, take));
  }

  @GetMapping("/search/tag")
  public ResultModel<List<DialogView>> searchTag(
      @RequestParam(required = false) String key, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(dialogs.searchTag(key, take));
  }

  @GetMapping("/one")
  public ResultModel<DialogView> one(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.one(dialogId));
  }

  @GetMapping("/user")
  public ResultModel<List<Long>> user(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.members(dialogId));
  }

  @GetMapping("/telephone")
  public ResultModel<DialogTelephoneView> telephone(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.telephone(dialogId));
  }

  @GetMapping("/open/user")
  public ResultModel<DialogView> openUser(@RequestParam long userId) {
    return ResultModel.ok(dialogs.openUser(userId));
  }

  @GetMapping("/open/event")
  public ResultModel<DialogView> openEvent(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.openEvent(dialogId));
  }

  @GetMapping("/session/create")
  public ResultModel<DialogSessionView> sessionCreate(
      @RequestParam long dialogId, @RequestParam(required = false) String title) {
    return ResultModel.ok(dialogs.sessionCreate(dialogId, title));
  }

  @GetMapping("/session/list")
  public ResultModel<List<DialogSessionView>> sessionList(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.sessionList(dialogId));
  }

  @GetMapping("/session/open")
  public ResultModel<DialogSessionView> sessionOpen(
      @RequestParam long dialogId, @RequestParam String sessionId) {
    return ResultModel.ok(dialogs.sessionOpen(dialogId, sessionId));
  }

  @PostMapping("/session/rename")
  public ResultModel<DialogSessionView> sessionRename(
      @RequestParam long dialogId,
      @RequestParam String sessionId,
      @RequestParam String title) {
    return ResultModel.ok(dialogs.sessionRename(dialogId, sessionId, title));
  }

  @GetMapping("/todo")
  public ResultModel<List<DialogMessageTodoView>> dialogTodo(
      @RequestParam long dialogId,
      @RequestParam(required = false, defaultValue = "0") int includeDone) {
    return ResultModel.ok(dialogs.dialogTodos(dialogId, includeDone != 0));
  }

  @GetMapping("/top")
  public ResultModel<DialogView> top(
      @RequestParam long dialogId,
      @RequestParam(required = false, defaultValue = "1") int isTop) {
    return ResultModel.ok(dialogs.top(dialogId, isTop != 0));
  }

  @GetMapping("/hide")
  public ResultModel<Void> hide(
      @RequestParam long dialogId,
      @RequestParam(required = false, defaultValue = "1") int isHidden) {
    dialogs.hide(dialogId, isHidden != 0);
    return ResultModel.ok();
  }

  @GetMapping("/config")
  public ResultModel<DialogConfigView> config(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.config(dialogId));
  }

  @PostMapping("/config/save")
  public ResultModel<DialogConfigView> configSave(
      @RequestParam long dialogId,
      @RequestParam(required = false) Integer isMuted,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) Integer isChatMuted) {
    return ResultModel.ok(dialogs.configSave(dialogId, isMuted, tag, isChatMuted));
  }

  @GetMapping("/message/silence")
  public ResultModel<DialogConfigView> messageSilence(
      @RequestParam long dialogId,
      @RequestParam(required = false, defaultValue = "1") int isSilent) {
    return ResultModel.ok(dialogs.messageSilence(dialogId, isSilent));
  }

  @GetMapping("/message/list")
  public ResultModel<List<DialogMessageView>> messageList(
      @RequestParam long dialogId,
      @RequestParam(required = false) Long beforeId,
      @RequestParam(required = false) Integer take) {
    return ResultModel.ok(dialogs.messageList(dialogId, beforeId, take));
  }

  @GetMapping("/message/latest")
  public ResultModel<List<DialogMessageView>> messageLatest(
      @RequestParam String dialogs, @RequestParam(required = false) Integer take) {
    return ResultModel.ok(this.dialogs.messageLatest(dialogs, take));
  }

  @PostMapping("/message/sendText")
  public ResultModel<DialogMessageView> sendText(
      @RequestParam long dialogId,
      @RequestParam String text,
      @RequestParam(required = false) Long replyId) {
    return ResultModel.ok(dialogs.sendText(dialogId, text, replyId));
  }

  @GetMapping("/message/one")
  public ResultModel<DialogMessageView> messageOne(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.messageOne(messageId));
  }

  @GetMapping("/message/detail")
  public ResultModel<?> messageDetail(
      @RequestParam long messageId, @RequestParam(required = false) String onlyUpdateAt) {
    return ResultModel.ok(dialogs.messageDetail(messageId, onlyUpdateAt));
  }

  @GetMapping("/message/download")
  public Object messageDownload(
      @RequestParam long messageId, @RequestParam(required = false, defaultValue = "yes") String down) {
    DialogMessageDownload result = dialogs.messageDownload(messageId, down);
    if (result.preview() || result.content() == null) {
      return ResultModel.ok(
          Map.of(
              "fileId", result.fileId(),
              "name", result.name() == null ? "" : result.name(),
              "url", result.url() == null ? "" : result.url(),
              "size", result.size()));
    }
    String filename = result.name() == null || result.name().isBlank() ? "file" : result.name();
    String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new InputStreamResource(result.content()));
  }

  @GetMapping("/message/mergeDetail")
  public ResultModel<DialogMergeDetailView> mergeDetail(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.mergeDetail(messageId));
  }

  @GetMapping("/message/dot")
  public ResultModel<Map<String, Object>> messageDot(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.messageDot(messageId));
  }

  @GetMapping("/message/checked")
  public ResultModel<DialogMessageView> messageChecked(
      @RequestParam long dialogId,
      @RequestParam long messageId,
      @RequestParam int index,
      @RequestParam int checked) {
    return ResultModel.ok(dialogs.messageChecked(dialogId, messageId, index, checked));
  }

  @PostMapping("/message/stream")
  public ResultModel<Void> messageStream(
      @RequestParam long userId,
      @RequestParam String streamUrl,
      @RequestParam(required = false) String source) {
    dialogs.messageStream(userId, streamUrl, source);
    return ResultModel.ok();
  }

  @GetMapping("/message/mark")
  public ResultModel<Map<String, Object>> messageMark(
      @RequestParam long dialogId,
      @RequestParam String type,
      @RequestParam(required = false) Long afterMessageId) {
    return ResultModel.ok(dialogs.messageMark(dialogId, type, afterMessageId));
  }

  @GetMapping("/message/tag")
  public ResultModel<DialogMessageTagView> messageTag(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.messageTag(messageId));
  }

  @GetMapping("/message/color")
  public ResultModel<DialogConfigView> messageColor(
      @RequestParam long dialogId, @RequestParam(required = false, defaultValue = "") String color) {
    return ResultModel.ok(dialogs.messageColor(dialogId, color));
  }

  @GetMapping("/message/translation")
  public ResultModel<DialogMessageTranslationView> messageTranslation(
      @RequestParam long messageId,
      @RequestParam String language,
      @RequestParam(required = false) Integer force) {
    return ResultModel.ok(dialogs.messageTranslation(messageId, language, force));
  }

  @GetMapping("/message/read")
  public ResultModel<Void> messageRead(
      @RequestParam long dialogId, @RequestParam(required = false) Long messageId) {
    dialogs.markRead(dialogId, messageId);
    return ResultModel.ok();
  }

  @GetMapping("/message/unread")
  public ResultModel<List<DialogUnreadItemView>> messageUnread() {
    return ResultModel.ok(dialogs.unread());
  }

  @GetMapping("/message/readList")
  public ResultModel<DialogMessageReadListView> messageReadList(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.readList(messageId));
  }

  @GetMapping("/message/sendFileId")
  public ResultModel<DialogMessageView> sendFileId(
      @RequestParam long dialogId,
      @RequestParam long fileId,
      @RequestParam(required = false) Long replyId) {
    return ResultModel.ok(dialogs.sendFileId(dialogId, fileId, replyId));
  }

  @GetMapping("/message/sendTaskId")
  public ResultModel<DialogMessageView> sendTaskId(
      @RequestParam long dialogId,
      @RequestParam long taskId,
      @RequestParam(required = false) String note,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) Long replyId) {
    String leave = note != null && !note.isBlank() ? note : text;
    return ResultModel.ok(dialogs.sendTaskId(dialogId, taskId, leave, replyId));
  }

  @PostMapping("/message/sendFile")
  public ResultModel<DialogMessageView> sendFile(
      @RequestParam long dialogId,
      @RequestParam(value = "files", required = false) MultipartFile files,
      @RequestParam(required = false) String filename,
      @RequestParam(required = false) Long replyId)
      throws IOException {
    if (files == null || files.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }
    String name =
        filename != null && !filename.isBlank()
            ? filename.trim()
            : (files.getOriginalFilename() == null ? "file" : files.getOriginalFilename());
    return ResultModel.ok(
        dialogs.sendFile(dialogId, name, files.getSize(), files.getInputStream(), replyId));
  }

  /** Base64 / data-URL 发图；{@code image} 为裸 base64 或 {@code data:image/...;base64,...}。 */
  @PostMapping("/message/image64")
  public ResultModel<DialogMessageView> image64(
      @RequestParam long dialogId,
      @RequestParam String image,
      @RequestParam(required = false) String filename,
      @RequestParam(required = false) Long replyId) {
    return ResultModel.ok(dialogs.sendImage64(dialogId, image, filename, replyId));
  }

  /** 在线表情：服务端拉取 {@code src} 后发图。 */
  @PostMapping("/message/sendSticker")
  public ResultModel<DialogMessageView> sendSticker(
      @RequestParam long dialogId,
      @RequestParam String src,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) Long replyId) {
    return ResultModel.ok(dialogs.sendSticker(dialogId, src, name, replyId));
  }

  /**
   * 群发文件：multipart {@code files}（可多份）+ {@code dialogId} 或逗号分隔 {@code dialogIds}。
   * 返回各目标会话产生的消息扁平列表。
   */
  @PostMapping("/message/sendFiles")
  public ResultModel<List<DialogMessageView>> sendFiles(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String dialogIds,
      @RequestParam("files") MultipartFile[] files)
      throws IOException {
    if (files == null || files.length == 0) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }
    List<Long> targets = new java.util.ArrayList<>();
    if (dialogIds != null && !dialogIds.isBlank()) {
      for (String part : dialogIds.split("[,;\\s]+")) {
        if (part.isBlank()) {
          continue;
        }
        try {
          long v = Long.parseLong(part.trim());
          if (v > 0 && !targets.contains(v)) {
            targets.add(v);
          }
        } catch (NumberFormatException e) {
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SEND_FILES_INVALID);
        }
      }
    } else if (dialogId != null && dialogId > 0) {
      targets.add(dialogId);
    }
    if (targets.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.DIALOG_SEND_FILES_INVALID);
    }
    List<DialogService.ChatFilePart> parts = new java.util.ArrayList<>();
    for (MultipartFile f : files) {
      if (f == null || f.isEmpty()) {
        continue;
      }
      String name = f.getOriginalFilename() == null ? "file" : f.getOriginalFilename();
      parts.add(new DialogService.ChatFilePart(name, f.getSize(), f.getBytes()));
    }
    if (parts.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.UPLOAD_CHUNK_EMPTY);
    }
    return ResultModel.ok(dialogs.sendFiles(targets, parts));
  }

  @GetMapping("/message/forward")
  public ResultModel<List<DialogMessageView>> forward(
      @RequestParam String messageIds, @RequestParam String dialogIds) {
    return ResultModel.ok(dialogs.forward(messageIds, dialogIds));
  }

  @GetMapping("/message/mergeForward")
  public ResultModel<DialogMessageView> mergeForward(
      @RequestParam String messageIds, @RequestParam long dialogId) {
    return ResultModel.ok(dialogs.mergeForward(messageIds, dialogId));
  }

  @GetMapping("/message/emoji")
  public ResultModel<List<DialogMessageEmojiView>> emoji(
      @RequestParam long messageId,
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false, defaultValue = "0") int cancel) {
    if (symbol == null || symbol.isBlank()) {
      return ResultModel.ok(dialogs.emojiList(messageId));
    }
    return ResultModel.ok(dialogs.emoji(messageId, symbol, cancel != 0));
  }

  @GetMapping("/message/emojiMap")
  public ResultModel<List<Map<String, Object>>> emojiMap(@RequestParam String messageIds) {
    return ResultModel.ok(dialogs.emojiMap(messageIds));
  }

  @GetMapping("/message/top")
  public ResultModel<List<DialogMessageView>> messageTop(
      @RequestParam long messageId, @RequestParam(required = false, defaultValue = "0") int cancel) {
    return ResultModel.ok(dialogs.messageTop(messageId, cancel != 0));
  }

  @GetMapping("/message/topInfo")
  public ResultModel<List<DialogMessageView>> messageTopInfo(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.topInfo(dialogId));
  }

  @GetMapping("/message/todo")
  public ResultModel<DialogMessageTodoView> messageTodo(
      @RequestParam long messageId, @RequestParam(required = false, defaultValue = "0") int cancel) {
    return ResultModel.ok(dialogs.todo(messageId, cancel != 0));
  }

  @GetMapping("/message/todoList")
  public ResultModel<List<DialogMessageTodoView>> messageTodoList(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false, defaultValue = "0") int includeDone) {
    return ResultModel.ok(dialogs.todoList(dialogId, includeDone != 0));
  }

  @GetMapping("/message/done")
  public ResultModel<DialogMessageTodoView> messageDone(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.todoDone(messageId));
  }

  @PostMapping("/message/todoRemind")
  public ResultModel<DialogMessageTodoView> messageTodoRemind(
      @RequestParam long messageId, @RequestParam(required = false) String remindAt) {
    return ResultModel.ok(dialogs.todoRemind(messageId, remindAt));
  }

  @PostMapping("/message/vote")
  public ResultModel<DialogMessageView> messageVote(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String options,
      @RequestParam(required = false) Long messageId,
      @RequestParam(required = false) String option,
      @RequestParam(required = false) Boolean end) {
    return ResultModel.ok(dialogs.vote(dialogId, title, options, messageId, option, end));
  }

  @PostMapping("/message/wordChain")
  public ResultModel<DialogMessageView> messageWordChain(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) Long messageId,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) Boolean stop) {
    return ResultModel.ok(dialogs.wordChain(dialogId, title, messageId, text, stop));
  }

  @GetMapping("/message/withdraw")
  public ResultModel<Void> withdraw(@RequestParam long messageId) {
    dialogs.withdraw(messageId);
    return ResultModel.ok();
  }

  @GetMapping("/group/add")
  public ResultModel<DialogView> groupAdd(
      @RequestParam(required = false) String chatName,
      @RequestParam String userIds,
      @RequestParam(required = false) String avatar) {
    return ResultModel.ok(dialogs.groupAdd(chatName, userIds, avatar));
  }

  @GetMapping("/group/edit")
  public ResultModel<DialogView> groupEdit(
      @RequestParam long dialogId,
      @RequestParam(required = false) String chatName,
      @RequestParam(required = false) String avatar) {
    return ResultModel.ok(dialogs.groupEdit(dialogId, chatName, avatar));
  }

  @GetMapping("/group/addUser")
  public ResultModel<List<Long>> groupAddUser(
      @RequestParam long dialogId, @RequestParam String userIds) {
    return ResultModel.ok(dialogs.groupAddUser(dialogId, userIds));
  }

  @GetMapping("/group/deleteUser")
  public ResultModel<List<Long>> groupDelUser(
      @RequestParam long dialogId, @RequestParam String userIds) {
    return ResultModel.ok(dialogs.groupDelUser(dialogId, userIds));
  }

  @GetMapping("/group/transfer")
  public ResultModel<DialogView> groupTransfer(
      @RequestParam long dialogId, @RequestParam long userId) {
    return ResultModel.ok(dialogs.groupTransfer(dialogId, userId));
  }

  @GetMapping("/group/addDeputy")
  public ResultModel<List<Long>> groupAddDeputy(
      @RequestParam long dialogId, @RequestParam long userId) {
    return ResultModel.ok(dialogs.groupAddDeputy(dialogId, userId));
  }

  @GetMapping("/group/deleteDeputy")
  public ResultModel<List<Long>> groupDelDeputy(
      @RequestParam long dialogId, @RequestParam long userId) {
    return ResultModel.ok(dialogs.groupDelDeputy(dialogId, userId));
  }

  @GetMapping("/group/deputies")
  public ResultModel<List<Long>> groupDeputies(@RequestParam long dialogId) {
    return ResultModel.ok(dialogs.groupDeputies(dialogId));
  }

  @GetMapping("/group/disband")
  public ResultModel<Void> groupDisband(@RequestParam long dialogId) {
    dialogs.groupDisband(dialogId);
    return ResultModel.ok();
  }

  @GetMapping("/group/searchUser")
  public ResultModel<Map<String, Object>> groupSearchUser(
      @RequestParam(required = false) String key) {
    return ResultModel.ok(dialogs.groupSearchUser(key));
  }

  @GetMapping("/common/list")
  public ResultModel<Map<String, Object>> commonList(
      @RequestParam(required = false) Long targetUserId,
      @RequestParam(required = false) String onlyCount,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    Integer size = pageSize;
    return ResultModel.ok(dialogs.commonList(targetUserId, onlyCount, page, size));
  }

  @PostMapping("/okr/add")
  public ResultModel<DialogView> okrAdd(
      @RequestParam long okrId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String userIds) {
    return ResultModel.ok(dialogs.okrAdd(okrId, name, userIds));
  }

  @PostMapping("/okr/push")
  public ResultModel<DialogMessageView> okrPush(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) Long okrId,
      @RequestParam String text) {
    return ResultModel.ok(dialogs.okrPush(dialogId, okrId, text));
  }

  @PostMapping("/message/sendNotice")
  public ResultModel<DialogMessageView> sendNotice(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String dialogIds,
      @RequestParam String notice,
      @RequestParam(required = false) String silence,
      @RequestParam(required = false) String source) {
    return ResultModel.ok(dialogs.sendNotice(dialogId, dialogIds, notice, silence, source));
  }

  @PostMapping("/message/sendAnon")
  public ResultModel<DialogMessageView> sendAnon(
      @RequestParam long userId, @RequestParam String text) {
    return ResultModel.ok(dialogs.sendAnon(userId, text));
  }

  @PostMapping("/message/sendBot")
  public ResultModel<DialogMessageView> sendBot(
      @RequestParam long userId,
      @RequestParam String text,
      @RequestParam(required = false) String botType,
      @RequestParam(required = false) String botName,
      @RequestParam(required = false) String silence) {
    return ResultModel.ok(dialogs.sendBot(userId, text, botType, botName, silence));
  }

  @PostMapping("/message/sendTemplate")
  public ResultModel<DialogMessageView> sendTemplate(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String dialogIds,
      @RequestParam String content,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) String silence,
      @RequestParam(required = false) String source) {
    return ResultModel.ok(dialogs.sendTemplate(dialogId, dialogIds, content, title, silence, source));
  }

  @PostMapping("/message/sendApprove")
  public ResultModel<DialogMessageView> sendApprove(
      @RequestParam long toUserId,
      @RequestParam String type,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) Integer isFinished,
      @RequestParam(required = false) String data,
      @RequestParam(required = false) String title) {
    return ResultModel.ok(dialogs.sendApprove(toUserId, type, action, isFinished, data, title));
  }

  @PostMapping("/message/sendRecord")
  public ResultModel<DialogMessageView> sendRecord(
      @RequestParam long dialogId,
      @RequestParam String base64,
      @RequestParam int duration,
      @RequestParam(required = false) Long replyId) {
    return ResultModel.ok(dialogs.sendRecord(dialogId, base64, duration, replyId));
  }

  @PostMapping("/message/convertRecord")
  public ResultModel<Map<String, String>> convertRecord(
      @RequestParam String base64,
      @RequestParam int duration,
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) String translate) {
    String text = dialogs.convertRecord(base64, duration, dialogId, translate);
    return ResultModel.ok(Map.of("text", text));
  }

  @GetMapping("/message/voiceToText")
  public ResultModel<DialogMessageView> voiceToText(@RequestParam long messageId) {
    return ResultModel.ok(dialogs.voiceToText(messageId));
  }

  @PostMapping("/message/sendAiAssistant")
  public ResultModel<DialogMessageView> sendAiAssistant(
      @RequestParam(required = false) Long dialogId,
      @RequestParam(required = false) Long taskId,
      @RequestParam String text,
      @RequestParam(required = false) String textType,
      @RequestParam(required = false) String silence,
      @RequestParam(required = false) String nickname) {
    return ResultModel.ok(
        dialogs.sendAiAssistant(dialogId, taskId, text, textType, silence, nickname));
  }

  @PostMapping("/message/sendLocation")
  public ResultModel<DialogMessageView> sendLocation(
      @RequestParam long dialogId,
      @RequestParam String type,
      @RequestParam double lng,
      @RequestParam double lat,
      @RequestParam String title,
      @RequestParam(required = false) Integer distance,
      @RequestParam(required = false) String address,
      @RequestParam(required = false) String thumb) {
    return ResultModel.ok(
        dialogs.sendLocation(dialogId, type, lng, lat, title, distance, address, thumb));
  }

  @GetMapping("/message/aiGenerate")
  public ResultModel<Map<String, Object>> aiGenerateGet() {
    return ResultModel.ok(dialogs.deprecatedMessageStub());
  }

  @PostMapping("/message/aiGenerate")
  public ResultModel<Map<String, Object>> aiGeneratePost() {
    return ResultModel.ok(dialogs.deprecatedMessageStub());
  }

  @GetMapping("/message/webhookMessageToAi")
  public ResultModel<Map<String, Object>> webhookMessageToAiGet() {
    return ResultModel.ok(dialogs.deprecatedMessageStub());
  }

  @PostMapping("/message/webhookMessageToAi")
  public ResultModel<Map<String, Object>> webhookMessageToAiPost() {
    return ResultModel.ok(dialogs.deprecatedMessageStub());
  }

  @GetMapping("/message/applied")
  public ResultModel<Map<String, Object>> appliedGet() {
    return ResultModel.ok(dialogs.deprecatedMessageStub());
  }

  @PostMapping("/message/applied")
  public ResultModel<Map<String, Object>> appliedPost() {
    return ResultModel.ok(dialogs.deprecatedMessageStub());
  }

  @GetMapping("/sticker/search")
  public ResultModel<Map<String, Object>> stickerSearch(@RequestParam(required = false) String key) {
    return ResultModel.ok(dialogs.stickerSearch(key));
  }
}
