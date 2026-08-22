package com.bluedock.file.web.dto;

import com.bluedock.file.domain.FileEntry;
import java.time.LocalDateTime;

public record FileView(
    long id,
    long parentId,
    String name,
    String type,
    String extension,
    long size,
    String hash,
    long userId,
    long createdUserId,
    int isShared,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static FileView from(FileEntry e) {
    return new FileView(
        e.getId(),
        e.getParentId(),
        e.getName(),
        e.getType(),
        e.getExtension() == null ? "" : e.getExtension(),
        e.getSize(),
        e.getHash() == null ? "" : e.getHash(),
        e.getUserId(),
        e.getCreatedUserId(),
        e.getIsShared(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
