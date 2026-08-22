package com.bluedock.messenger.todo;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.todo.TodoAlertRemindBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import com.bluedock.messenger.service.DialogService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessengerTodoAlertRemindBridge implements TodoAlertRemindBridge {
  private static final Logger log = LoggerFactory.getLogger(MessengerTodoAlertRemindBridge.class);

  private final UserAccountRepository users;
  private final DialogRepository dialogs;
  private final DialogService dialogService;

  public MessengerTodoAlertRemindBridge(
      UserAccountRepository users, DialogRepository dialogs, DialogService dialogService) {
    this.users = users;
    this.dialogs = dialogs;
    this.dialogService = dialogService;
  }

  @Override
  @Transactional
  public long sendDm(long userId, String text) {
    if (userId <= 0 || text == null || text.isBlank()) {
      return 0L;
    }
    Optional<UserAccount> bot = users.findByEmail(BOT_EMAIL);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      log.warn("todo-alert bot missing; skip remind");
      return 0L;
    }
    if (!users.existsByUserId(userId)) {
      return 0L;
    }
    long botUserId = bot.get().getUserId();
    if (botUserId == userId) {
      return 0L;
    }
    try {
      long dialogId = ensureUserDialog(botUserId, userId);
      return dialogService.sendTextAsBot(botUserId, dialogId, text.trim()).id();
    } catch (Exception e) {
      log.warn("todo remind dm to {} failed: {}", userId, e.toString());
      return 0L;
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
}
