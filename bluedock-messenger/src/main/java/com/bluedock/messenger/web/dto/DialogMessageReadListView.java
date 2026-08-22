package com.bluedock.messenger.web.dto;

import java.util.List;

public record DialogMessageReadListView(long messageId, List<Long> reads, List<Long> unreads) {}
