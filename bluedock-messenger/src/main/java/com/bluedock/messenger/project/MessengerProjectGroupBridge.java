package com.bluedock.messenger.project;

import com.bluedock.common.project.ProjectGroupBridge;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.repo.DialogRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MessengerProjectGroupBridge implements ProjectGroupBridge {
  private final DialogRepository dialogs;

  public MessengerProjectGroupBridge(DialogRepository dialogs) {
    this.dialogs = dialogs;
  }

  @Override
  @Transactional
  public long ensureGroup(
      long projectId, String name, long ownerUserId, Collection<Long> memberIds) {
    Dialog existing = dialogs.findByGroupLink("project", projectId).orElse(null);
    long dialogId;
    if (existing == null) {
      LocalDateTime now = LocalDateTime.now();
      Dialog d = new Dialog();
      d.setId(IdGenerator.nextId());
      d.setType("group");
      d.setGroupType("project");
      d.setName(name == null || name.isBlank() ? "Project" : name.trim());
      d.setAvatar("");
      d.setOwnerId(ownerUserId);
      d.setLinkId(projectId);
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
      if (!want.contains(userId)) {
        dialogs.deleteMember(dialogId, userId);
      }
    }
  }

  @Override
  @Transactional
  public void disbandByLink(long projectId) {
    dialogs.findByGroupLink("project", projectId).ifPresent(d -> dialogs.softDeleteDialog(d.getId()));
  }
}
