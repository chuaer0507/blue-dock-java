package com.bluedock.messenger.web.dto;

import java.util.List;

/** 合并转发详情：内嵌消息条目。 */
public record DialogMergeDetailView(List<DialogMessageView> messages) {}
