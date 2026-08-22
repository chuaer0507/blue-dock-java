package com.bluedock.messenger.web.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** 消息详情；{@code file} 在 type=file/image 时附带网盘元数据。 */
public record DialogMessageDetailView(
    long id,
    long dialogId,
    long userId,
    String type,
    String body,
    long replyId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Map<String, Object> file) {}
