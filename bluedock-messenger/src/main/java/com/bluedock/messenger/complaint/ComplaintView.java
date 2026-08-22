package com.bluedock.messenger.complaint;

import java.time.LocalDateTime;
import java.util.List;

/** 举报列表项（camelCase wire）。 */
public record ComplaintView(
    long id,
    long dialogId,
    long userId,
    int type,
    String reason,
    List<String> images,
    int status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static ComplaintView from(Complaint c) {
    return new ComplaintView(
        c.getId(),
        c.getDialogId(),
        c.getUserId(),
        c.getType(),
        c.getReason() == null ? "" : c.getReason(),
        c.getImages() == null ? List.of() : c.getImages(),
        c.getStatus(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
