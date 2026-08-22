package com.bluedock.messenger.project;

import com.bluedock.auth.domain.UserAccount;
import com.bluedock.auth.repo.UserAccountRepository;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.project.TaskGroupBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessengerTaskGroupBridge implements TaskGroupBridge {
  private final DialogRepository dialogs;
  private final UserAccountRepository users;

  public MessengerTaskGroupBridge(DialogRepository dialogs, UserAccountRepository users) {
    this.dialogs = dialogs;
    this.users = users;
  }

  @Override
  @Transactional
  public long ensureGroup(long taskId, String name, long ownerUserId, Collection<Long> memberIds) {
    Dialog existing = dialogs.findByGroupLink("task", taskId).orElse(null);
    long dialogId;
    if (existing == null) {
      LocalDateTime now = LocalDateTime.now();
      Dialog d = new Dialog();
      d.setId(IdGenerator.nextId());
      d.setType("group");
      d.setGroupType("task");
      d.setName(
          name == null || name.isBlank()
              ? Messages.get(I18nKeys.TASK_GROUP_DEFAULT_NAME)
              : name.trim());
      d.setAvatar("");
      d.setOwnerId(ownerUserId);
      d.setLinkId(taskId);
      d.setLastMessage("");
      d.setLastAt(now);
      d.setCreatedAt(now);
      dialogs.insertDialog(d);
      dialogId = d.getId();
    } else {
      dialogId = existing.getId();
      String n = name == null || name.isBlank() ? existing.getName() : name.trim();
      dialogs.updateDialogMeta(dialogId, n, existing.getAvatar());
      if (ownerUserId > 0 && existing.getOwnerId() != ownerUserId) {
        dialogs.updateOwner(dialogId, ownerUserId);
      }
    }
    Set<Long> members = new HashSet<>();
    if (memberIds != null) {
      members.addAll(memberIds);
    }
    if (ownerUserId > 0) {
      members.add(ownerUserId);
    }
    syncMembers(dialogId, members);
    return dialogId;
  }

  @Override
  @Transactional
  public void syncMembers(long dialogId, Collection<Long> memberIds) {
    Set<Long> want = new HashSet<>();
    if (memberIds != null) {
      for (Long id : memberIds) {
        if (id != null && id > 0) {
          want.add(id);
        }
      }
    }
    List<Long> current = dialogs.listMemberUserIds(dialogId);
    for (Long userId : want) {
      if (!dialogs.isMember(dialogId, userId)) {
        dialogs.insertMember(IdGenerator.nextId(), dialogId, userId);
      }
    }
    for (Long userId : current) {
      if (!want.contains(userId) && !isBot(userId)) {
        dialogs.deleteMember(dialogId, userId);
      }
    }
  }

  @Override
  @Transactional
  public void disbandByLink(long taskId) {
    dialogs.findByGroupLink("task", taskId).ifPresent(d -> dialogs.softDeleteDialog(d.getId()));
  }

  private boolean isBot(long userId) {
    Optional<UserAccount> u = users.findByUserId(userId);
    return u.isPresent() && u.get().getIsBot() == 1;
  }
}
