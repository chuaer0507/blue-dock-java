package com.bluedock.file.service;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.file.domain.FileEntry;
import com.bluedock.file.domain.FileUser;
import com.bluedock.file.repo.FileRepository;
import com.bluedock.file.repo.FileUserRepository;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;

/** 文件访问：拥有者或共享祖先链上的成员。permission：0=只读，1=读写；拥有者视为 1。 */
@Service
public class FileAccessService {
  public static final int PERM_READ = 0;
  public static final int PERM_WRITE = 1;

  private final FileRepository files;
  private final FileUserRepository fileUsers;

  public FileAccessService(FileRepository files, FileUserRepository fileUsers) {
    this.files = files;
    this.fileUsers = fileUsers;
  }

  public FileEntry requireReadable(long id, long userId) {
    FileEntry f = requireExists(id);
    if (resolvePermission(f, userId).isEmpty()) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    return f;
  }

  public FileEntry requireWritable(long id, long userId) {
    FileEntry f = requireExists(id);
    OptionalInt perm = resolvePermission(f, userId);
    if (perm.isEmpty() || perm.getAsInt() < PERM_WRITE) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    return f;
  }

  public FileEntry requireOwner(long id, long userId) {
    FileEntry f = requireExists(id);
    if (f.getUserId() != userId) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    return f;
  }

  /** 拥有者或创建者可删。 */
  public FileEntry requireOwnerOrCreator(long id, long userId) {
    FileEntry f = requireExists(id);
    if (f.getUserId() != userId && f.getCreatedUserId() != userId) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    return f;
  }

  /** 软删条目：拥有者或创建者可恢复。 */
  public FileEntry requireOwnerOrCreatorDeleted(long id, long userId) {
    FileEntry f =
        files
            .findIncludingDeleted(id)
            .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
    if (f.getDeletedAt() == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.FILE_NOT_IN_TRASH);
    }
    if (f.getUserId() != userId && f.getCreatedUserId() != userId) {
      throw new BusinessException(ErrorCodes.FILE_DENIED, I18nKeys.FILE_DENIED);
    }
    return f;
  }

  public OptionalInt resolvePermission(FileEntry start, long userId) {
    if (start.getUserId() == userId) {
      return OptionalInt.of(PERM_WRITE);
    }
    long cur = start.getId();
    FileEntry node = start;
    for (int i = 0; i < 64; i++) {
      OptionalInt shared =
          fileUsers.findActive(cur, userId).map(FileUser::getPermission).stream()
              .mapToInt(Integer::intValue)
              .findFirst();
      if (shared.isPresent()) {
        return shared;
      }
      if (node.getParentId() <= 0) {
        return OptionalInt.empty();
      }
      long parentId = node.getParentId();
      node =
          files
              .findActive(parentId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
      cur = node.getId();
    }
    return OptionalInt.empty();
  }

  private FileEntry requireExists(long id) {
    return files
        .findActive(id)
        .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.FILE_NOT_FOUND));
  }
}
