package com.bluedock.messenger.web.dto;

import java.util.List;

public record DialogUnreadItemView(
    long dialogId, int unreadCount, int mentionCount, List<Long> mentionIds, long lastReadMessageId) {}
