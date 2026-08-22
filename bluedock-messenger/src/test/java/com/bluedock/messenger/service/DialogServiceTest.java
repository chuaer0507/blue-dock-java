package com.bluedock.messenger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.realtime.RealtimeFanoutPublisher;
import com.bluedock.common.search.SearchIndexPublisher;
import com.bluedock.common.project.TaskCardBridge;
import com.bluedock.common.project.TaskDialogAccessBridge;
import com.bluedock.common.project.TaskDialogOpenBridge;
import com.bluedock.common.project.TaskMentionBridge;
import com.bluedock.common.upload.DialogChatFileSink;
import com.bluedock.messenger.bot.UserBotWebhookDispatchService;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.domain.DialogMessage;
import com.bluedock.messenger.notify.DialogAppPushNotifyService;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.sticker.StickerSearchService;
import com.bluedock.messenger.web.dto.DialogMessageDetailView;
import com.bluedock.messenger.web.dto.DialogMessageDownload;
import com.bluedock.messenger.web.dto.DialogMessageTagView;
import com.bluedock.messenger.web.dto.DialogMessageTranslationView;
import com.bluedock.messenger.web.dto.DialogMessageView;
import com.bluedock.messenger.web.dto.DialogMergeDetailView;
import com.bluedock.messenger.web.dto.DialogTelephoneView;
import com.bluedock.messenger.web.dto.DialogView;
import com.bluedock.system.ai.AiBotChatService;
import com.bluedock.system.service.AdminGuard;
import com.bluedock.system.service.SystemGeneralSettingService;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DialogServiceTest {
  @Mock DialogRepository dialogs;
  @Mock UserAccountRepository users;
  @Mock RealtimeFanoutPublisher fanout;
  @Mock SearchIndexPublisher searchIndex;
  @Mock UserBotWebhookDispatchService userBotWebhook;
  @Mock org.springframework.beans.factory.ObjectProvider<DialogChatFileSink> chatFiles;
  @Mock org.springframework.beans.factory.ObjectProvider<TaskMentionBridge> taskMentions;
  @Mock org.springframework.beans.factory.ObjectProvider<TaskDialogAccessBridge> taskDialogAccess;
  @Mock org.springframework.beans.factory.ObjectProvider<TaskDialogOpenBridge> taskDialogOpen;
  @Mock org.springframework.beans.factory.ObjectProvider<TaskCardBridge> taskCards;
  @Mock org.springframework.beans.factory.ObjectProvider<SystemGeneralSettingService> systemSettings;
  @Mock org.springframework.beans.factory.ObjectProvider<org.springframework.security.crypto.password.PasswordEncoder>
      passwordEncoder;
  @Mock org.springframework.beans.factory.ObjectProvider<com.bluedock.common.oss.ObjectStorage> objectStorage;
  @Mock org.springframework.beans.factory.ObjectProvider<com.bluedock.system.ai.AiBotChatService> aiBotChat;
  @Mock SystemGeneralSettingService generalSettings;
  @Mock AdminGuard adminGuard;
  @Mock DialogAppPushNotifyService appPushNotify;
  @Mock StickerSearchService stickerSearch;
  @Mock org.springframework.beans.factory.ObjectProvider<com.bluedock.messenger.session.DialogSessionTitleService>
      sessionTitles;

  DialogService service;

  @BeforeEach
  void login() {
    AuthContext.set(new AuthUser(1L));
    lenient().when(taskDialogAccess.getIfAvailable()).thenReturn(null);
    lenient().when(taskDialogOpen.getIfAvailable()).thenReturn(null);
    lenient().when(taskCards.getIfAvailable()).thenReturn(null);
    lenient().when(systemSettings.getIfAvailable()).thenReturn(generalSettings);
    lenient().when(passwordEncoder.getIfAvailable()).thenReturn(null);
    lenient().when(objectStorage.getIfAvailable()).thenReturn(null);
    lenient().when(aiBotChat.getIfAvailable()).thenReturn(null);
    lenient().when(sessionTitles.getIfAvailable()).thenReturn(null);
    lenient().when(stickerSearch.search(any())).thenReturn(List.of());
    lenient().when(generalSettings.messageRecallLimitMinutes()).thenReturn(0);
    lenient().when(generalSettings.isUserPrivateChatMuteOpen()).thenReturn(true);
    lenient().when(generalSettings.isUserGroupChatMuteOpen()).thenReturn(true);
    lenient().when(generalSettings.isAllGroupMuteOpen()).thenReturn(false);
    lenient().when(generalSettings.isAnonMessageOpen()).thenReturn(true);
    lenient().when(adminGuard.isAdmin(anyLong())).thenReturn(false);
    lenient().when(dialogs.findChatMuted(anyLong())).thenReturn(0);
    service =
        new DialogService(
            dialogs,
            users,
            fanout,
            searchIndex,
            userBotWebhook,
            chatFiles,
            taskMentions,
            taskDialogAccess,
            taskDialogOpen,
            taskCards,
            systemSettings,
            passwordEncoder,
            objectStorage,
            aiBotChat,
            adminGuard,
            appPushNotify,
            stickerSearch,
            sessionTitles);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void openUser_creates() {
    when(users.existsByUserId(2L)).thenReturn(true);
    when(dialogs.findUserDialogId(1L, 2L)).thenReturn(Optional.empty());

    DialogView view = service.openUser(2L);
    assertEquals("user", view.type());
    verify(dialogs).insertDialog(any(Dialog.class));
    verify(dialogs).insertMember(anyLong(), anyLong(), eq(1L));
    verify(dialogs).insertMember(anyLong(), anyLong(), eq(2L));
  }

  @Test
  void sendText_recordsUserMention() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(users.findByUserId(1L)).thenReturn(Optional.empty());
    when(users.findByUserId(2L)).thenReturn(Optional.empty());

    String body = "<span class=\"mention user\" data-id=\"2\">@bob</span> hi";
    DialogMessageView msg = service.sendText(9L, body, null);
    assertEquals("text", msg.type());
    verify(dialogs).bumpMention(eq(9L), eq(2L), anyLong());
  }

  @Test
  void sendText_recordsMentionAllExceptSenderAndBot() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L, 3L));
    UserAccount bot = new UserAccount();
    bot.setIsBot(1);
    when(users.findByUserId(1L)).thenReturn(Optional.empty());
    when(users.findByUserId(2L)).thenReturn(Optional.empty());
    when(users.findByUserId(3L)).thenReturn(Optional.of(bot));

    String body = "<span class=\"mention all\">@所有人</span>";
    service.sendText(9L, body, null);
    verify(dialogs).bumpMention(eq(9L), eq(2L), anyLong());
    verify(dialogs, never()).bumpMention(eq(9L), eq(1L), anyLong());
    verify(dialogs, never()).bumpMention(eq(9L), eq(3L), anyLong());
  }

  @Test
  void sendText_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    DialogMessageView msg = service.sendText(9L,"hello", null);
    assertEquals("hello", msg.body());
    verify(dialogs).insertMessage(any(DialogMessage.class));
    verify(dialogs).bumpUnreadExcept(9L, 1L);
    verify(fanout).publish(any());
  }

  @Test
  void sendText_recordsTaskMentions() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L));
    TaskMentionBridge bridge = org.mockito.Mockito.mock(TaskMentionBridge.class);
    when(taskMentions.getIfAvailable()).thenReturn(bridge);

    String body = "<span class=\"mention task\" data-id=\"77\">#T</span>";
    DialogMessageView msg = service.sendText(9L, body, null);
    verify(bridge)
        .recordMentionsFromMessage(eq(9L), eq(msg.id()), eq(1L), eq(body));
  }

  @Test
  void sendText_empty() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    assertThrows(BusinessException.class, () -> service.sendText(9L, "", null));
  }

  @Test
  void lists_ok() {
    Dialog d = new Dialog();
    d.setId(1L);
    d.setType("user");
    when(dialogs.listForUser(1L)).thenReturn(List.of(d));
    assertEquals(1, service.lists().size());
  }

  @Test
  void groupAdd_ok() {
    when(users.existsByUserId(1L)).thenReturn(true);
    when(users.existsByUserId(2L)).thenReturn(true);

    DialogView view = service.groupAdd("Team","2", null);
    assertEquals("group", view.type());
    assertEquals("user", view.groupType());
    assertEquals("Team", view.name());
    verify(dialogs).insertDialog(any(Dialog.class));
  }

  @Test
  void groupAdd_rejectsBot() {
    when(users.existsByUserId(1L)).thenReturn(true);
    when(users.existsByUserId(2L)).thenReturn(true);
    when(users.findByUserId(1L)).thenReturn(Optional.empty());
    com.bluedock.auth.domain.UserAccount bot = new com.bluedock.auth.domain.UserAccount();
    bot.setUserId(2L);
    bot.setIsBot(1);
    when(users.findByUserId(2L)).thenReturn(Optional.of(bot));
    assertThrows(BusinessException.class, () -> service.groupAdd("Team","2", null));
  }

  @Test
  void groupExit_viaDelUser() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    d.setOwnerId(2L);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(2L));
    assertEquals(List.of(2L), service.groupDelUser(9L,"1"));
    verify(dialogs).deleteMember(9L, 1L);
  }

  @Test
  void sendText_botDmDenied() {
    Dialog d = new Dialog();
    d.setType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(users.findByUserId(1L)).thenReturn(Optional.empty());
    com.bluedock.auth.domain.UserAccount bot = new com.bluedock.auth.domain.UserAccount();
    bot.setUserId(2L);
    bot.setIsBot(1);
    when(users.findByUserId(2L)).thenReturn(Optional.of(bot));
    assertThrows(BusinessException.class, () -> service.sendText(9L,"hi", null));
  }

  @Test
  void groupDisband_ownerOnly() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    d.setOwnerId(2L);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    assertThrows(BusinessException.class, () -> service.groupDisband(9L));
  }

  @Test
  void withdraw_ownMessage() {
    DialogMessage msg = new DialogMessage();
    msg.setId(5L);
    msg.setDialogId(9L);
    msg.setUserId(1L);
    msg.setCreatedAt(LocalDateTime.now());
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(msg));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));

    service.withdraw(5L);
    verify(dialogs).softDeleteMessage(5L);
  }

  @Test
  void withdraw_expired() {
    DialogMessage msg = new DialogMessage();
    msg.setId(5L);
    msg.setDialogId(9L);
    msg.setUserId(1L);
    msg.setCreatedAt(LocalDateTime.now().minusMinutes(40));
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(msg));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    Dialog d = new Dialog();
    d.setType("group");
    d.setGroupType("user");
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(generalSettings.messageRecallLimitMinutes()).thenReturn(30);
    when(users.findByUserId(1L)).thenReturn(Optional.empty());

    assertThrows(BusinessException.class, () -> service.withdraw(5L));
  }

  @Test
  void withdraw_botAuthor_unlimited() {
    DialogMessage msg = new DialogMessage();
    msg.setId(5L);
    msg.setDialogId(9L);
    msg.setUserId(1L);
    msg.setCreatedAt(LocalDateTime.now().minusDays(2));
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(msg));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    com.bluedock.auth.domain.UserAccount bot = new com.bluedock.auth.domain.UserAccount();
    bot.setUserId(1L);
    bot.setIsBot(1);
    when(users.findByUserId(1L)).thenReturn(Optional.of(bot));

    service.withdraw(5L);
    verify(dialogs).softDeleteMessage(5L);
  }

  @Test
  void markRead_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    service.markRead(9L, 100L);
    verify(dialogs).markMessageReadsUpTo(eq(9L), eq(1L), eq(100L), any());
    verify(dialogs).clearUnread(9L, 1L, 100L);
  }

  @Test
  void sendFileId_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.findFileMeta(7L))
        .thenReturn(
            Optional.of(
                java.util.Map.of(
                    "id", 7L,"name","a.png","type","picture","extension","png","size", 10L,
                    "path","file/x","userId", 1L)));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    DialogMessageView msg = service.sendFileId(9L, 7L, null);
    assertEquals("image", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void forward_ok() {
    DialogMessage src = new DialogMessage();
    src.setId(5L);
    src.setDialogId(9L);
    src.setUserId(1L);
    src.setType("text");
    src.setBody("hi");
    when(dialogs.findMessagesByIds(List.of(5L))).thenReturn(List.of(src));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.isMember(10L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.findActive(10L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(10L)).thenReturn(List.of(1L, 3L));
    assertEquals(1, service.forward("5","10").size());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void emoji_toggle() {
    DialogMessage msg = new DialogMessage();
    msg.setId(5L);
    msg.setDialogId(9L);
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(msg));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
    row.put("userId", 1L);
    row.put("symbol","👍");
    row.put("createdAt", java.time.LocalDateTime.now());
    when(dialogs.listEmojis(5L)).thenReturn(List.of(row));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    assertEquals(1, service.emoji(5L,"👍", false).size());
    verify(dialogs).insertEmoji(anyLong(), eq(5L), eq(1L), eq("👍"), any());
  }

  @Test
  void messageTop_ok() {
    DialogMessage msg = new DialogMessage();
    msg.setId(5L);
    msg.setDialogId(9L);
    msg.setType("text");
    msg.setBody("pin");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(msg));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.listTopMessageIds(9L)).thenReturn(List.of(5L));
    when(dialogs.findMessagesByIds(List.of(5L))).thenReturn(List.of(msg));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    assertEquals(1, service.messageTop(5L, false).size());
    verify(dialogs).insertMessageTop(anyLong(), eq(9L), eq(5L), eq(1L), any());
  }

  @Test
  void todo_ok() {
    DialogMessage msg = new DialogMessage();
    msg.setId(5L);
    msg.setDialogId(9L);
    msg.setType("text");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(msg));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findTodo(5L, 1L)).thenReturn(Optional.empty());
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L));
    assertEquals(5L, service.todo(5L, false).messageId());
    verify(dialogs).insertTodo(anyLong(), eq(5L), eq(9L), eq(1L), any());
  }

  @Test
  void vote_createAndCast() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    DialogMessageView created = service.vote(9L,"午餐？","面,饭", null, null, null);
    assertEquals("vote", created.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));

    DialogMessage existing = new DialogMessage();
    existing.setId(7L);
    existing.setDialogId(9L);
    existing.setUserId(1L);
    existing.setType("vote");
    existing.setBody(created.body());
    when(dialogs.findMessage(7L)).thenReturn(Optional.of(existing));

    DialogMessageView cast = service.vote(null, null, null, 7L,"0", null);
    assertEquals(true, cast.body().contains("\"votes\":[1]"));
    verify(dialogs).updateMessageBody(eq(7L), any(), any());
  }

  @Test
  void wordChain_createAndJoin() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    DialogMessageView created = service.wordChain(9L,"周末去哪", null, null, null);
    assertEquals("wordChain", created.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));

    DialogMessage existing = new DialogMessage();
    existing.setId(8L);
    existing.setDialogId(9L);
    existing.setUserId(1L);
    existing.setType("wordChain");
    existing.setBody(created.body());
    when(dialogs.findMessage(8L)).thenReturn(Optional.of(existing));

    DialogMessageView joined = service.wordChain(null, null, 8L,"爬山", null);
    assertEquals(true, joined.body().contains("爬山"));
    verify(dialogs).updateMessageBody(eq(8L), any(), any());
  }

  @Test
  void configSave_muteUserGroup() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    java.util.Map<String, Object> flags = new java.util.LinkedHashMap<>();
    flags.put("isTop", 0);
    flags.put("isHidden", 0);
    flags.put("isMuted", 1);
    flags.put("tag","");
    when(dialogs.findUserFlags(9L, 1L)).thenReturn(Optional.of(flags));

    assertEquals(1, service.configSave(9L, 1, null, null).isMuted());
    verify(dialogs).setIsMuted(9L, 1L, true);
  }

  @Test
  void configSave_muteDeniedOnUserDialog() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    assertThrows(BusinessException.class, () -> service.configSave(9L, 1, null, null));
  }

  @Test
  void configSave_chatMuteByOwner() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    d.setOwnerId(1L);
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.findChatMuted(9L)).thenReturn(1);
    java.util.Map<String, Object> flags = new java.util.LinkedHashMap<>();
    flags.put("isTop", 0);
    flags.put("isHidden", 0);
    flags.put("isMuted", 0);
    flags.put("tag", "");
    when(dialogs.findUserFlags(9L, 1L)).thenReturn(Optional.of(flags));

    assertEquals(1, service.configSave(9L, null, null, 1).isChatMuted());
    verify(dialogs).upsertChatMuted(anyLong(), eq(9L), eq(true), any());
  }

  @Test
  void sendText_deniedWhenChatMuted() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    d.setOwnerId(2L);
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.findChatMuted(9L)).thenReturn(1);
    when(dialogs.isDeputy(9L, 1L)).thenReturn(false);
    assertThrows(BusinessException.class, () -> service.sendText(9L, "hi", null));
    verify(dialogs, never()).insertMessage(any());
  }

  @Test
  void openEvent_triggersWebhook() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    d.setName("g");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listForUser(1L)).thenReturn(List.of(d));
    DialogView view = service.openEvent(9L);
    assertEquals(9L, view.id());
    verify(userBotWebhook).afterDialogOpen(d, 1L);
  }

  @Test
  void dialogTodos_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.listTodos(1L, 9L, false)).thenReturn(List.of());
    assertEquals(0, service.dialogTodos(9L, false).size());
  }

  @Test
  void search_ok() {
    Dialog d = new Dialog();
    d.setId(3L);
    d.setType("group");
    d.setGroupType("user");
    d.setName("研发群");
    when(dialogs.searchForUser(eq(1L), any(), eq(50))).thenReturn(List.of(d));
    assertEquals(1, service.search("研发", null).size());
  }

  @Test
  void beyond_ok() {
    when(dialogs.listHiddenForUser(1L)).thenReturn(List.of());
    assertEquals(0, service.beyond().size());
  }

  @Test
  void sendFile_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    DialogChatFileSink sink = (userId, dialogId, filename, size, content) ->
        new DialogChatFileSink.Saved(77L, filename,"file","txt", size,"chat/9/x/content");
    when(chatFiles.getIfAvailable()).thenReturn(sink);
    java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
    meta.put("userId", 1L);
    meta.put("name","a.txt");
    meta.put("type","file");
    meta.put("extension","txt");
    meta.put("size", 3L);
    meta.put("path","chat/9/x/content");
    when(dialogs.findFileMeta(77L)).thenReturn(Optional.of(meta));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    DialogMessageView msg =
        service.sendFile(9L,"a.txt", 3L, new ByteArrayInputStream("abc".getBytes()), null);
    assertEquals("file", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void sendFiles_multiDialog() {
    Dialog d9 = new Dialog();
    d9.setId(9L);
    d9.setType("group");
    d9.setGroupType("user");
    Dialog d10 = new Dialog();
    d10.setId(10L);
    d10.setType("group");
    d10.setGroupType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.isMember(10L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d9));
    when(dialogs.findActive(10L)).thenReturn(Optional.of(d10));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberUserIds(10L)).thenReturn(List.of(1L, 3L));
    DialogChatFileSink sink =
        (userId, dialogId, filename, size, content) ->
            new DialogChatFileSink.Saved(88L, filename, "file", "txt", size, "chat/9/x");
    when(chatFiles.getIfAvailable()).thenReturn(sink);
    java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
    meta.put("userId", 1L);
    meta.put("name", "a.txt");
    meta.put("type", "file");
    meta.put("extension", "txt");
    meta.put("size", 3L);
    meta.put("path", "chat/9/x");
    when(dialogs.findFileMeta(88L)).thenReturn(Optional.of(meta));

    var parts =
        List.of(new DialogService.ChatFilePart("a.txt", 3L, "abc".getBytes()));
    assertEquals(2, service.sendFiles(List.of(9L, 10L), parts).size());
    verify(dialogs, org.mockito.Mockito.times(2)).insertMessage(any(DialogMessage.class));
  }

  @Test
  void sendFiles_rejectsEmptyTargets() {
    assertThrows(
        BusinessException.class,
        () ->
            service.sendFiles(
                List.of(), List.of(new DialogService.ChatFilePart("a.txt", 1L, new byte[] {1}))));
  }

  @Test
  void sendImage64_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    DialogChatFileSink sink =
        (userId, dialogId, filename, size, content) ->
            new DialogChatFileSink.Saved(99L, filename, "picture", "png", size, "chat/9/x");
    when(chatFiles.getIfAvailable()).thenReturn(sink);
    java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
    meta.put("userId", 1L);
    meta.put("name", "image.png");
    meta.put("type", "picture");
    meta.put("extension", "png");
    meta.put("size", 3L);
    meta.put("path", "chat/9/x");
    when(dialogs.findFileMeta(99L)).thenReturn(Optional.of(meta));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    String dataUrl = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
    DialogMessageView msg = service.sendImage64(9L, dataUrl, null, null);
    assertEquals("image", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void sendImage64_invalid() {
    assertThrows(BusinessException.class, () -> service.sendImage64(9L, "not-base64!!!", null, null));
  }

  @Test
  void sessionCreateAndList() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.findUserSessionKey(9L, 1L)).thenReturn("abc");
    when(dialogs.listDialogSessions(9L, 1L))
        .thenReturn(
            List.of(
                Map.of(
                    "dialogId",
                    9L,
                    "sessionKey",
                    "abc",
                    "title",
                    "New chat",
                    "createdAt",
                    LocalDateTime.now(),
                    "updatedAt",
                    LocalDateTime.now())));

    var created = service.sessionCreate(9L, null);
    assertEquals(1, created.isCurrent());
    verify(dialogs).insertDialogSession(anyLong(), eq(9L), eq(1L), any(), eq("New chat"), any());
    verify(dialogs).setUserSessionKey(eq(9L), eq(1L), any());

    assertEquals(1, service.sessionList(9L).size());
  }

  @Test
  void sendTaskId_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    TaskCardBridge bridge =
        new TaskCardBridge() {
          @Override
          public Map<String, Object> buildCard(long taskId, long userId, String note) {
            return Map.of("id", taskId,"taskId", taskId,"name","Demo","note", note == null ? "" : note);
          }

          @Override
          public void linkFromDialogIfTaskGroup(long dialogId, long messageId, long taskId, long userId) {}
        };
    when(taskCards.getIfAvailable()).thenReturn(bridge);

    DialogMessageView msg = service.sendTaskId(9L, 50L,"请看这个", null);
    assertEquals("task", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void groupSearchUser_requiresAdminAndReturnsList() {
    Dialog d = new Dialog();
    d.setId(3L);
    d.setType("group");
    d.setGroupType("user");
    d.setName("研发群");
    when(dialogs.searchUserGroups("研发", 20)).thenReturn(List.of(d));

    Map<String, Object> data = service.groupSearchUser("研发");
    verify(adminGuard).requireAdmin();
    List<DialogView> list = (List<DialogView>) data.get("list");
    assertEquals(1, list.size());
    assertEquals(3L, list.get(0).id());
    assertEquals("研发群", list.get(0).name());
  }

  @Test
  @SuppressWarnings("unchecked")
  void commonList_withTargetAndPagination() {
    when(users.existsByUserId(2L)).thenReturn(true);
    when(dialogs.countCommonUserGroups(1L, 2L)).thenReturn(1L);
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("user");
    d.setName("共同群");
    when(dialogs.pageCommonUserGroups(1L, 2L, 0, 20)).thenReturn(List.of(d));

    Map<String, Object> data = service.commonList(2L, null, 1, 20);
    assertEquals(1L, data.get("total"));
    assertEquals(1, data.get("page"));
    List<DialogView> list = (List<DialogView>) data.get("list");
    assertEquals(1, list.size());
    assertEquals("共同群", list.get(0).name());
  }

  @Test
  void commonList_onlyCount() {
    when(dialogs.countCommonUserGroups(1L, null)).thenReturn(5L);
    Map<String, Object> data = service.commonList(null,"yes", null, null);
    assertEquals(5L, data.get("total"));
    assertEquals(1, data.size());
  }

  @Test
  void messageList_taskDialog_deniedWhenNotVisible() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("task");
    d.setLinkId(50L);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    TaskDialogAccessBridge bridge = (taskId, userId) -> false;
    when(taskDialogAccess.getIfAvailable()).thenReturn(bridge);
    assertThrows(BusinessException.class, () -> service.messageList(9L, null, 20));
  }

  @Test
  void okrAdd_createsGroupWithBot() {
    when(dialogs.findByGroupLink("okr", 42L)).thenReturn(Optional.empty());
    when(users.existsByUserId(1L)).thenReturn(true);
    when(users.existsByUserId(2L)).thenReturn(true);
    UserAccount bot = new UserAccount();
    bot.setUserId(88L);
    bot.setIsBot(1);
    when(users.findByEmail("okr-alert@bot.system")).thenReturn(Optional.of(bot));
    when(dialogs.isMember(anyLong(), eq(1L))).thenReturn(false, true);
    when(dialogs.isMember(anyLong(), eq(2L))).thenReturn(false);
    when(dialogs.isMember(anyLong(), eq(88L))).thenReturn(false);
    when(dialogs.listMemberUserIds(anyLong())).thenReturn(List.of());
    when(dialogs.listForUser(1L)).thenReturn(List.of());
    when(dialogs.findActive(anyLong()))
        .thenAnswer(
            inv -> {
              Dialog d = new Dialog();
              d.setId(((Number) inv.getArgument(0)).longValue());
              d.setType("group");
              d.setGroupType("okr");
              d.setLinkId(42L);
              d.setName("Q1 OKR");
              d.setOwnerId(1L);
              return Optional.of(d);
            });

    DialogView view = service.okrAdd(42L, "Q1 OKR", "2");
    assertEquals("okr", view.groupType());
    assertEquals(42L, view.linkId());
    verify(dialogs).insertDialog(any(Dialog.class));
    verify(dialogs, org.mockito.Mockito.atLeast(3)).insertMember(anyLong(), anyLong(), anyLong());
  }

  @Test
  void okrAdd_rejectsInvalidId() {
    assertThrows(BusinessException.class, () -> service.okrAdd(0L, null, null));
  }

  @Test
  void okrPush_byOkrId() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("group");
    d.setGroupType("okr");
    d.setLinkId(42L);
    when(dialogs.findByGroupLink("okr", 42L)).thenReturn(Optional.of(d));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    UserAccount bot = new UserAccount();
    bot.setUserId(88L);
    bot.setIsBot(1);
    when(users.findByEmail("okr-alert@bot.system")).thenReturn(Optional.of(bot));
    when(users.findByUserId(88L)).thenReturn(Optional.of(bot));
    when(dialogs.isMember(9L, 88L)).thenReturn(true);
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 88L));

    DialogMessageView msg = service.okrPush(null, 42L, "KR updated");
    assertEquals("text", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void sendNotice_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of(1L, false, 2L, false));

    DialogMessageView msg = service.sendNotice(9L, null, "hello notice", null, null);
    assertEquals("notice", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
    verify(dialogs).bumpUnreadExcept(9L, 1L);
  }

  @Test
  void sendTemplate_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of());

    DialogMessageView msg =
        service.sendTemplate(
            9L, null, "[{\"content\":\"Hello card\",\"style\":\"color:red\"}]", null, null, null);
    assertEquals("template", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
    verify(dialogs).bumpUnreadExcept(9L, 1L);
  }

  @Test
  void sendApprove_ok() {
    UserAccount peer = new UserAccount();
    peer.setUserId(2L);
    peer.setIsBot(0);
    when(users.findByUserId(2L)).thenReturn(Optional.of(peer));
    UserAccount bot = new UserAccount();
    bot.setUserId(66L);
    bot.setIsBot(1);
    when(users.findByEmail("approval-alert@bot.system")).thenReturn(Optional.of(bot));
    when(dialogs.findUserDialogId(66L, 2L)).thenReturn(Optional.of(18L));
    when(dialogs.findActive(18L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(18L)).thenReturn(List.of(66L, 2L));
    when(dialogs.listMemberMutes(18L)).thenReturn(Map.of());

    DialogMessageView msg =
        service.sendApprove(
            2L,
            "approve_reviewer",
            "start",
            0,
            "{\"approveId\":1}",
            "Please review");
    assertEquals("template", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
    verify(dialogs, never()).bumpUnreadExcept(anyLong(), anyLong());
  }

  @Test
  void sendRecord_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of());
    com.bluedock.common.oss.ObjectStorage storage =
        org.mockito.Mockito.mock(com.bluedock.common.oss.ObjectStorage.class);
    when(objectStorage.getIfAvailable()).thenReturn(storage);
    when(storage.put(anyString(), any(), anyLong(), anyString())).thenReturn("/chat/9/x.mp3");

    String b64 =
        "data:audio/mp3;base64," + java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4});
    DialogMessageView msg = service.sendRecord(9L, b64, 1200, null);
    assertEquals("record", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
    verify(dialogs).bumpUnreadExcept(9L, 1L);
  }

  @Test
  void convertRecord_ok() {
    AiBotChatService ai = org.mockito.Mockito.mock(AiBotChatService.class);
    when(aiBotChat.getIfAvailable()).thenReturn(ai);
    when(ai.available()).thenReturn(true);
    when(ai.transcribe(any(), anyString(), anyString(), any())).thenReturn("hello");
    when(users.findByUserId(1L)).thenReturn(Optional.of(new UserAccount()));

    String b64 =
        "data:audio/wav;base64," + java.util.Base64.getEncoder().encodeToString(new byte[] {9, 8, 7});
    assertEquals("hello", service.convertRecord(b64, 800, null, null));
  }

  @Test
  void voiceToText_cached() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setType("record");
    m.setBody("{\"path\":\"chat/9/a.mp3\",\"text\":\"hi\",\"textUserId\":[]}");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);

    DialogMessageView view = service.voiceToText(5L);
    assertEquals("record", view.type());
    verify(dialogs).updateMessageBody(eq(5L), anyString(), any(LocalDateTime.class));
  }

  @Test
  void sendAiAssistant_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 55L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of());
    when(dialogs.isMember(9L, 55L)).thenReturn(true);
    UserAccount bot = new UserAccount();
    bot.setUserId(55L);
    bot.setIsBot(1);
    when(users.findByEmail("ai-openai@bot.system")).thenReturn(Optional.of(bot));

    DialogMessageView msg =
        service.sendAiAssistant(9L, null, "hello from ai", "md", "yes", "助手");
    assertEquals("text", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
    verify(dialogs, never()).bumpUnreadExcept(anyLong(), anyLong());
  }

  @Test
  void sendLocation_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of());

    DialogMessageView msg =
        service.sendLocation(9L, "amap", 121.47, 31.23, "外滩", 100, "上海", null);
    assertEquals("location", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void deprecatedMessageStub_ok() {
    Map<String, Object> out = service.deprecatedMessageStub();
    assertEquals(true, out.get("deprecated"));
  }

  @Test
  void stickerSearch_ok() {
    when(stickerSearch.search("猫"))
        .thenReturn(List.of(Map.of("name", "cat", "src", "https://x/a.gif", "height", 20, "width", 20)));
    Map<String, Object> out = service.stickerSearch("猫");
    assertEquals(1, ((List<?>) out.get("list")).size());
  }

  @Test
  void sendAnon_ok() {
    UserAccount peer = new UserAccount();
    peer.setUserId(2L);
    peer.setIsBot(0);
    when(users.findByUserId(2L)).thenReturn(Optional.of(peer));
    UserAccount bot = new UserAccount();
    bot.setUserId(77L);
    bot.setIsBot(1);
    when(users.findByEmail("anon-msg@bot.system")).thenReturn(Optional.of(bot));
    when(dialogs.findUserDialogId(77L, 2L)).thenReturn(Optional.of(15L));
    when(dialogs.findActive(15L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(15L)).thenReturn(List.of(77L, 2L));
    when(dialogs.listMemberMutes(15L)).thenReturn(Map.of());

    DialogMessageView msg = service.sendAnon(2L, "secret");
    assertEquals("text", msg.type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void sendAnon_disabled() {
    when(generalSettings.isAnonMessageOpen()).thenReturn(false);
    assertThrows(BusinessException.class, () -> service.sendAnon(2L, "x"));
  }

  @Test
  void sendBot_systemMsg() {
    UserAccount peer = new UserAccount();
    peer.setUserId(2L);
    peer.setIsBot(0);
    when(users.findByUserId(2L)).thenReturn(Optional.of(peer));
    UserAccount bot = new UserAccount();
    bot.setUserId(66L);
    bot.setIsBot(1);
    when(users.findByEmail("system-msg@bot.system")).thenReturn(Optional.of(bot));
    when(dialogs.findUserDialogId(66L, 2L)).thenReturn(Optional.of(20L));
    when(dialogs.findActive(20L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(20L)).thenReturn(List.of(66L, 2L));
    when(dialogs.listMemberMutes(20L)).thenReturn(Map.of());

    DialogMessageView msg = service.sendBot(2L, "hi **md**", "system-msg", null, "yes");
    assertEquals("text", msg.type());
    verify(dialogs, never()).bumpUnreadExcept(anyLong(), anyLong());
  }

  @Test
  void telephone_ok() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of(1L, false, 2L, false));

    UserAccount me = new UserAccount();
    me.setUserId(1L);
    me.setNickname("Alice");
    me.setIdentity("[]");
    UserAccount peer = new UserAccount();
    peer.setUserId(2L);
    peer.setNickname("Bob");
    peer.setTelephone("13800138000");
    when(users.findByUserId(1L)).thenReturn(Optional.of(me));
    when(users.findByUserId(2L)).thenReturn(Optional.of(peer));

    DialogTelephoneView view = service.telephone(9L);
    assertEquals("13800138000", view.telephone());
    assertEquals("notice", view.add().type());
    verify(dialogs).insertMessage(any(DialogMessage.class));
  }

  @Test
  void telephone_rejectsTemporary() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    UserAccount me = new UserAccount();
    me.setUserId(1L);
    me.setIdentity("[\"temporary\"]");
    UserAccount peer = new UserAccount();
    peer.setUserId(2L);
    peer.setTelephone("13800138000");
    when(users.findByUserId(1L)).thenReturn(Optional.of(me));
    when(users.findByUserId(2L)).thenReturn(Optional.of(peer));

    assertThrows(BusinessException.class, () -> service.telephone(9L));
    verify(dialogs, never()).insertMessage(any(DialogMessage.class));
  }

  @Test
  void telephone_rejectsEmptyTelephone() {
    Dialog d = new Dialog();
    d.setId(9L);
    d.setType("user");
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(d));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    UserAccount peer = new UserAccount();
    peer.setUserId(2L);
    peer.setTelephone("");
    when(users.findByUserId(2L)).thenReturn(Optional.of(peer));

    assertThrows(BusinessException.class, () -> service.telephone(9L));
  }

  @Test
  void messageLatest_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    DialogMessage m = new DialogMessage();
    m.setId(12L);
    m.setDialogId(9L);
    m.setUserId(2L);
    m.setType("text");
    m.setBody("hi");
    when(dialogs.listMessagesAfter(9L, 10L, 25)).thenReturn(List.of(m));

    List<DialogMessageView> views = service.messageLatest("[{\"id\":9,\"latestId\":10}]", null);
    assertEquals(1, views.size());
    assertEquals(12L, views.get(0).id());
  }

  @Test
  void messageDetail_onlyUpdateAt() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setUserId(1L);
    m.setType("text");
    m.setBody("x");
    m.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
    m.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> slim = (Map<String, Object>) service.messageDetail(5L, "yes");
    assertEquals(5L, slim.get("id"));
    assertEquals(LocalDateTime.of(2026, 1, 2, 0, 0), slim.get("updatedAt"));
  }

  @Test
  void messageDetail_withFile() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setUserId(1L);
    m.setType("file");
    m.setBody("{\"fileId\":77,\"name\":\"a.pdf\"}");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findFileMeta(77L)).thenReturn(Optional.of(Map.of("id", 77L, "name", "a.pdf")));

    DialogMessageDetailView view = (DialogMessageDetailView) service.messageDetail(5L, null);
    assertEquals("file", view.type());
    assertEquals("a.pdf", view.file().get("name"));
  }

  @Test
  void messageDownload_preview() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setType("file");
    m.setBody("{\"fileId\":77}");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findFileMeta(77L))
        .thenReturn(Optional.of(Map.of("id", 77L, "name", "a.pdf", "path", "chat/9/a", "size", 3L)));

    DialogMessageDownload down = service.messageDownload(5L, "preview");
    assertEquals(true, down.preview());
    assertEquals("/chat/9/a", down.url());
  }

  @Test
  void mergeDetail_ok() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setType("merge");
    m.setBody(
        "{\"merge\":true,\"items\":[{\"messageId\":1,\"userId\":2,\"type\":\"text\",\"body\":\"hi\"}]}");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);

    DialogMergeDetailView view = service.mergeDetail(5L);
    assertEquals(1, view.messages().size());
    assertEquals("hi", view.messages().get(0).body());
  }

  @Test
  void messageDot_ok() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);

    Map<String, Object> out = service.messageDot(5L);
    assertEquals(5L, out.get("messageId"));
    assertEquals(0, out.get("dot"));
    verify(dialogs).clearMessageDot(5L, 1L);
  }

  @Test
  void messageChecked_ok() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setUserId(1L);
    m.setType("text");
    m.setBody("<ul><li data-list=\"unchecked\">a</li><li>b</li></ul>");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));

    DialogMessageView view = service.messageChecked(9L, 5L, 0, 1);
    assertEquals(true, view.body().contains("data-list=\"checked\""));
    verify(dialogs).updateMessageBody(eq(5L), any(String.class), any(LocalDateTime.class));
  }

  @Test
  void messageChecked_rejectsOthers() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setUserId(2L);
    m.setType("text");
    m.setBody("<li>a</li>");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);

    assertThrows(BusinessException.class, () -> service.messageChecked(9L, 5L, 0, 1));
  }

  @Test
  void messageStream_ok() {
    when(users.existsByUserId(2L)).thenReturn(true);
    service.messageStream(2L, "/stream/x", "ai");
    verify(fanout).publish(any());
  }

  @Test
  void messageMark_unread() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findMemberFlags(9L, 1L))
        .thenReturn(
            Optional.of(
                Map.of(
                    "unreadCount",
                    3,
                    "mentionCount",
                    0,
                    "mentionIds",
                    "",
                    "lastReadMessageId",
                    0L,
                    "markUnread",
                    1,
                    "updatedAt",
                    LocalDateTime.now())));

    Map<String, Object> out = service.messageMark(9L, "unread", null);
    assertEquals(1, out.get("markUnread"));
    verify(dialogs).setMarkUnread(9L, 1L, true);
  }

  @Test
  void messageTag_ok() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setType("text");
    m.setBody("hi");
    m.setTagUserId(0L);
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findActive(9L)).thenReturn(Optional.of(new Dialog()));
    when(dialogs.listMemberUserIds(9L)).thenReturn(List.of(1L, 2L));
    when(dialogs.listMemberMutes(9L)).thenReturn(Map.of());

    DialogMessageTagView view = service.messageTag(5L);
    assertEquals(1L, view.tag());
    verify(dialogs).updateMessageTagUserId(eq(5L), eq(1L), any(LocalDateTime.class));
  }

  @Test
  void messageColor_ok() {
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findUserFlags(9L, 1L))
        .thenReturn(Optional.of(Map.of("isTop", 0, "isHidden", 0, "isMuted", 0, "tag", "", "color", "#ff0000")));
    when(dialogs.findChatMuted(9L)).thenReturn(0);
    var out = service.messageColor(9L, "#ff0000");
    assertEquals("#ff0000", out.color());
    verify(dialogs).setMemberColor(9L, 1L, "#ff0000");
  }

  @Test
  void messageTranslation_cached() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setType("text");
    m.setBody("你好");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findTranslation(5L, "en-US"))
        .thenReturn(Optional.of(Map.of("messageId", 5L, "language", "en-US", "content", "Hello")));

    DialogMessageTranslationView view = service.messageTranslation(5L, "en-US", 0);
    assertEquals("Hello", view.content());
  }

  @Test
  void messageTranslation_callsAi() {
    DialogMessage m = new DialogMessage();
    m.setId(5L);
    m.setDialogId(9L);
    m.setType("text");
    m.setBody("你好");
    when(dialogs.findMessage(5L)).thenReturn(Optional.of(m));
    when(dialogs.isMember(9L, 1L)).thenReturn(true);
    when(dialogs.findTranslation(5L, "en-US")).thenReturn(Optional.empty());
    AiBotChatService ai = org.mockito.Mockito.mock(AiBotChatService.class);
    when(aiBotChat.getIfAvailable()).thenReturn(ai);
    when(ai.available()).thenReturn(true);
    when(ai.chat(any(), eq("你好"))).thenReturn("Hello");

    DialogMessageTranslationView view = service.messageTranslation(5L, "en-US", null);
    assertEquals("Hello", view.content());
    verify(dialogs)
        .upsertTranslation(anyLong(), eq(9L), eq(5L), eq("en-US"), eq("Hello"), any(LocalDateTime.class));
  }
}
