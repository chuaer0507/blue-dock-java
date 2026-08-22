package com.bluedock.messenger.web.dto;

import com.bluedock.messenger.domain.DialogMessage;
import java.time.LocalDateTime;

public record DialogMessageView(
    long id,
    long dialogId,
    long userId,
    String type,
    String body,
    long replyId,
    long tagUserId,
    LocalDateTime createdAt) {

  public static DialogMessageView from(DialogMessage m) {
    return new DialogMessageView(
        m.getId(),
        m.getDialogId(),
        m.getUserId(),
        m.getType(),
        m.getBody() == null ? "" : m.getBody(),
        m.getReplyId(),
        m.getTagUserId(),
        m.getCreatedAt());
  }
}
