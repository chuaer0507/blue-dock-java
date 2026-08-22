package com.bluedock.messenger.web.dto;

/** 消息翻译结果。 */
public record DialogMessageTranslationView(long messageId, String language, String content) {}
