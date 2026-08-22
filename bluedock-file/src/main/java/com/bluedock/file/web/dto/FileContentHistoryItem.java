package com.bluedock.file.web.dto;

import com.bluedock.file.domain.FileContent;
import java.time.LocalDateTime;

/** 历史列表条目（不含正文，减小 payload）。 */
public record FileContentHistoryItem(long id, long size, long userId, LocalDateTime createdAt) {

  public static FileContentHistoryItem from(FileContent c) {
    return new FileContentHistoryItem(c.getId(), c.getSize(), c.getUserId(), c.getCreatedAt());
  }
}
