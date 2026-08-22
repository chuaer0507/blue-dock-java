package com.bluedock.common.bot;

/** Kafka {@code bluedock.userBot.webhook.reply} — Webhook 返回的机器人回复。 */
public record UserBotWebhookReplyEvent(
    String eventId, long botUserId, long dialogId, long replyToMessageId, String text) {}
