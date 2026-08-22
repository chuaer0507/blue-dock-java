package com.bluedock.messenger.web.dto;

import java.time.LocalDateTime;

public record DialogMessageTodoView(
    long id,
    long messageId,
    long dialogId,
    long userId,
    LocalDateTime remindAt,
    LocalDateTime doneAt,
    LocalDateTime createdAt) {}
