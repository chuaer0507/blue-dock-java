package com.bluedock.file.web.dto;

import com.bluedock.file.domain.FileContent;
import java.time.LocalDateTime;

public record FileContentView(
    long id,
    long fileId,
    String content,
    String text,
    long size,
    long userId,
    LocalDateTime createdAt) {

  public static FileContentView from(FileContent c) {
    return new FileContentView(
        c.getId(),
        c.getFileId(),
        c.getContent() == null ? "" : c.getContent(),
        c.getText() == null ? "" : c.getText(),
        c.getSize(),
        c.getUserId(),
        c.getCreatedAt());
  }
}
