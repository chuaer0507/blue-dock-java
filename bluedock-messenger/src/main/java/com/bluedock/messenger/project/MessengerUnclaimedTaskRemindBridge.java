package com.bluedock.messenger.project;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.project.UnclaimedTaskRemindBridge;
import com.bluedock.messenger.service.DialogService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessengerUnclaimedTaskRemindBridge implements UnclaimedTaskRemindBridge {
  private static final Logger log = LoggerFactory.getLogger(MessengerUnclaimedTaskRemindBridge.class);

  private final UserAccountRepository users;
  private final DialogService dialogService;

  public MessengerUnclaimedTaskRemindBridge(
      UserAccountRepository users, DialogService dialogService) {
    this.users = users;
    this.dialogService = dialogService;
  }

  @Override
  @Transactional
  public long sendToDialog(long dialogId, String text) {
    if (dialogId <= 0 || text == null || text.isBlank()) {
      return 0L;
    }
    Optional<UserAccount> bot = users.findByEmail(BOT_EMAIL);
    if (bot.isEmpty() || bot.get().getIsBot() != 1) {
      log.warn("task-alert bot missing; skip unclaimed remind");
      return 0L;
    }
    try {
      return dialogService.sendTextAsBot(bot.get().getUserId(), dialogId, text.trim()).id();
    } catch (Exception e) {
      log.warn("unclaimed remind dialog {} failed: {}", dialogId, e.toString());
      return 0L;
    }
  }
}
