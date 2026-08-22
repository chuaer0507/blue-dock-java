package com.bluedock.messenger.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DialogMessageEmojiView(String symbol, List<Long> userIds, LocalDateTime firstAt) {}
