package com.bluedock.messenger.web.dto;

/** 消息标注结果；{@code tag} 为标注者 userId，0 表示已取消；{@code add} 为附带 notice/tag 消息。 */
public record DialogMessageTagView(long messageId, long tag, DialogMessageView add) {}
