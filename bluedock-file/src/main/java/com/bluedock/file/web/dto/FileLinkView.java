package com.bluedock.file.web.dto;

import com.bluedock.file.domain.FileLink;
import java.time.LocalDateTime;

public record FileLinkView(
    long id,
    long fileId,
    String code,
    int permission,
    int allowGuest,
    long userId,
    LocalDateTime createdAt) {

  public static FileLinkView from(FileLink link) {
    return new FileLinkView(
        link.getId(),
        link.getFileId(),
        link.getCode(),
        link.getPermission(),
        link.getAllowGuest(),
        link.getUserId(),
        link.getCreatedAt());
  }
}
