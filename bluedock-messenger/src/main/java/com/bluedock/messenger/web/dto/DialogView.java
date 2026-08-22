package com.bluedock.messenger.web.dto;

import com.bluedock.messenger.domain.Dialog;
import com.bluedock.messenger.mention.DialogMentionParser;
import java.time.LocalDateTime;
import java.util.List;

public record DialogView(
    long id,
    String type,
    String groupType,
    String name,
    String avatar,
    long ownerId,
    long linkId,
    String lastMessage,
    LocalDateTime lastAt,
    int unreadCount,
    int mentionCount,
    List<Long> mentionIds,
    int isTop,
    String color,
    LocalDateTime createdAt) {

  public static DialogView from(Dialog d) {
    return new DialogView(
        d.getId(),
        d.getType(),
        d.getGroupType() == null ? "" : d.getGroupType(),
        d.getName() == null ? "" : d.getName(),
        d.getAvatar() == null ? "" : d.getAvatar(),
        d.getOwnerId(),
        d.getLinkId(),
        d.getLastMessage() == null ? "" : d.getLastMessage(),
        d.getLastAt(),
        d.getUnreadCount(),
        d.getMentionCount(),
        DialogMentionParser.parseIdsCsv(d.getMentionIds()),
        d.getIsTop(),
        d.getColor() == null ? "" : d.getColor(),
        d.getCreatedAt());
  }
}
