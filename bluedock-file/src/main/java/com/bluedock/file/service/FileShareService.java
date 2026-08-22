package com.bluedock.file.service;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.util.IdGenerator;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.domain.FileLink;
import com.bluedock.file.domain.FileUser;
import com.bluedock.file.repo.FileLinkRepository;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.repo.FileUserRepository;
import com.bluedock.file.web.dto.FileLinkView;
import com.bluedock.file.web.dto.FileShareMemberView;
import com.bluedock.file.web.dto.FileShareView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileShareService {
  private static final int MAX_MEMBERS = 100;

  private final FileRepository files;
  private final FileUserRepository fileUsers;
  private final FileLinkRepository fileLinks;
  private final FileAccessService access;

  public FileShareService(
      FileRepository files,
      FileUserRepository fileUsers,
      FileLinkRepository fileLinks,
      FileAccessService access) {
    this.files = files;
    this.fileUsers = fileUsers;
    this.fileLinks = fileLinks;
    this.access = access;
  }

  public FileShareView share(long id) {
    long userId = AuthContext.requireUserId();
    FileEntry file = access.requireReadable(id, userId);
    return toView(file);
  }

  @Transactional
  public FileShareView update(
      long id, String userIds, String removeUserIds, Integer permission) {
    long me = AuthContext.requireUserId();
    FileEntry file = access.requireOwner(id, me);
    rejectNestedShare(file);

    int perm =
        permission == null
            ? FileAccessService.PERM_READ
            : (permission > 0 ? FileAccessService.PERM_WRITE : FileAccessService.PERM_READ);
    LocalDateTime now = LocalDateTime.now();

    for (long userId : parseIds(removeUserIds)) {
      if (userId == me || userId == file.getUserId()) {
        continue;
      }
      fileUsers.hardDelete(id, userId);
    }

    List<Long> addIds = parseIds(userIds);
    int afterAdd = fileUsers.countByFileId(id);
    for (long userId : addIds) {
      if (userId <= 0 || userId == me || userId == file.getUserId()) {
        continue;
      }
      var existing = fileUsers.findActive(id, userId);
      if (existing.isPresent()) {
        fileUsers.updatePermission(existing.get().getId(), perm, now);
        continue;
      }
      if (afterAdd >= MAX_MEMBERS) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_SHARE_MEMBERS);
      }
      FileUser row = new FileUser();
      row.setId(IdGenerator.nextId());
      row.setFileId(id);
      row.setUserId(userId);
      row.setPermission(perm);
      row.setCreatedAt(now);
      row.setUpdatedAt(now);
      fileUsers.insert(row);
      afterAdd++;
    }

    int members = fileUsers.countByFileId(id);
    int shareFlag = members > 0 ? 1 : file.getIsShared();
    if (members > 0) {
      shareFlag = 1;
    } else if (fileLinks.findActiveByFileId(id).isEmpty()) {
      shareFlag = 0;
    }
    files.updateShare(id, shareFlag, now);
    file.setIsShared(shareFlag);
    return toView(file);
  }

  @Transactional
  public void shareOut(long id) {
    long me = AuthContext.requireUserId();
    FileEntry file = access.requireReadable(id, me);
    if (file.getUserId() == me) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_SHARE_MEMBERS);
    }
    long shareRoot = findShareRootForMember(file, me);
    if (shareRoot <= 0) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    fileUsers.hardDelete(shareRoot, me);
  }

  @Transactional
  public FileLinkView link(long id, Boolean refresh, Integer permission, Integer allowGuest) {
    long me = AuthContext.requireUserId();
    access.requireOwner(id, me);
    LocalDateTime now = LocalDateTime.now();
    int perm =
        permission == null
            ? FileAccessService.PERM_READ
            : (permission > 0 ? FileAccessService.PERM_WRITE : FileAccessService.PERM_READ);
    int allowGuestFlag = allowGuest != null && allowGuest > 0 ? 1 : 0;
    boolean doRefresh = refresh != null && refresh;

    var existing = fileLinks.findActiveByFileId(id);
    if (existing.isPresent() && !doRefresh) {
      FileLink link = existing.get();
      if (permission != null || allowGuest != null) {
        int p = permission != null ? perm : link.getPermission();
        int g = allowGuest != null ? allowGuestFlag : link.getAllowGuest();
        fileLinks.updateMeta(link.getId(), p, g, now);
        link.setPermission(p);
        link.setAllowGuest(g);
        link.setUpdatedAt(now);
      }
      return FileLinkView.from(link);
    }

    if (existing.isPresent()) {
      fileLinks.softDeleteByFileId(id, now);
    }
    FileLink link = new FileLink();
    link.setId(IdGenerator.nextId());
    link.setFileId(id);
    link.setCode(UUID.randomUUID().toString().replace("-", ""));
    link.setPermission(perm);
    link.setAllowGuest(allowGuestFlag);
    link.setUserId(me);
    link.setCreatedAt(now);
    link.setUpdatedAt(now);
    fileLinks.insert(link);
    files.updateShare(id, 1, now);
    return FileLinkView.from(link);
  }

  public FileLinkView linkByCode(String code) {
    String c = code == null ? "" : code.trim();
    if (c.isEmpty()) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_LINK_NOT_FOUND);
    }
    FileLink link =
        fileLinks
            .findActiveByCode(c)
            .orElseThrow(
                () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_LINK_NOT_FOUND));
    if (link.getAllowGuest() <= 0) {
      AuthContext.requireUserId();
    }
    files
        .findActive(link.getFileId())
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    return FileLinkView.from(link);
  }

  void cleanupShare(long fileId, LocalDateTime at) {
    fileUsers.softDeleteByFileId(fileId, at);
    fileLinks.softDeleteByFileId(fileId, at);
  }

  private FileShareView toView(FileEntry file) {
    List<FileShareMemberView> members =
        fileUsers.listByFileId(file.getId()).stream().map(FileShareMemberView::from).toList();
    FileLinkView link = fileLinks.findActiveByFileId(file.getId()).map(FileLinkView::from).orElse(null);
    return new FileShareView(file.getId(), file.getIsShared(), members, link);
  }

  private void rejectNestedShare(FileEntry file) {
    long cur = file.getParentId();
    for (int i = 0; i < 64 && cur > 0; i++) {
      FileEntry parent =
          files
              .findActive(cur)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
      if (parent.getIsShared() > 0 || !fileUsers.listByFileId(parent.getId()).isEmpty()) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_SHARE_NESTED);
      }
      cur = parent.getParentId();
    }
  }

  private long findShareRootForMember(FileEntry start, long userId) {
    long cur = start.getId();
    FileEntry node = start;
    for (int i = 0; i < 64; i++) {
      if (fileUsers.findActive(cur, userId).isPresent()) {
        return cur;
      }
      if (node.getParentId() <= 0) {
        return 0;
      }
      node = files.findActive(node.getParentId()).orElse(null);
      if (node == null) {
        return 0;
      }
      cur = node.getId();
    }
    return 0;
  }

  private static List<Long> parseIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    Set<Long> ids = new LinkedHashSet<>();
    for (String part : raw.split("[,;\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      try {
        long v = Long.parseLong(part.trim());
        if (v > 0) {
          ids.add(v);
        }
      } catch (NumberFormatException ignored) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_SHARE_MEMBERS);
      }
    }
    return new ArrayList<>(ids);
  }
}
