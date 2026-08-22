package com.bluedock.messenger.web.dto;

/**
 * 会话配置：个人免打扰/置顶/隐藏/标注/颜色 + 会话级群禁言。
 *
 * <p>{@code isMuted} = 个人免打扰；{@code isChatMuted} = 群禁言（仅群主/管理员可改）。
 */
public record DialogConfigView(
    long dialogId,
    int isMuted,
    int isTop,
    int isHidden,
    String tag,
    int isChatMuted,
    String color) {}
