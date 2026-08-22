package com.bluedock.messenger.domain;

import java.time.LocalDateTime;

public class Dialog {
  private long id;
  private String type;
  private String groupType;
  private String name;
  private String avatar;
  private long ownerId;
  private long linkId;
  private String lastMessage;
  private LocalDateTime lastAt;
  private int unreadCount;
  private int mentionCount;
  private String mentionIds;
  private int isTop;
  private String color;
  private LocalDateTime createdAt;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getGroupType() {
    return groupType;
  }

  public void setGroupType(String groupType) {
    this.groupType = groupType;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAvatar() {
    return avatar;
  }

  public void setAvatar(String avatar) {
    this.avatar = avatar;
  }

  public long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(long ownerId) {
    this.ownerId = ownerId;
  }

  public long getLinkId() {
    return linkId;
  }

  public void setLinkId(long linkId) {
    this.linkId = linkId;
  }

  public String getLastMessage() {
    return lastMessage;
  }

  public void setLastMessage(String lastMessage) {
    this.lastMessage = lastMessage;
  }

  public LocalDateTime getLastAt() {
    return lastAt;
  }

  public void setLastAt(LocalDateTime lastAt) {
    this.lastAt = lastAt;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public void setUnreadCount(int unreadCount) {
    this.unreadCount = unreadCount;
  }

  public int getMentionCount() {
    return mentionCount;
  }

  public void setMentionCount(int mentionCount) {
    this.mentionCount = mentionCount;
  }

  public String getMentionIds() {
    return mentionIds;
  }

  public void setMentionIds(String mentionIds) {
    this.mentionIds = mentionIds;
  }

  public int getIsTop() {
    return isTop;
  }

  public void setIsTop(int isTop) {
    this.isTop = isTop;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
