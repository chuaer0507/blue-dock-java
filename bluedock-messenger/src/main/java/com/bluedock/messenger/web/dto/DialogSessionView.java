package com.bluedock.messenger.web.dto;

import java.time.LocalDateTime;

/** 对话侧 AI 会话（api/dialog/session/*）。 */
public record DialogSessionView(
    long dialogId,
    String sessionId,
    String title,
    int isCurrent,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
