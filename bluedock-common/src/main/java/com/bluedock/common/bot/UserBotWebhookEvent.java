package com.bluedock.common.bot;

import java.util.Map;

/** Kafka {@code bluedock.userBot.webhook} 载荷（用户自建机器人事件）。 */
public record UserBotWebhookEvent(
    String eventId,
    String event,
    String webhookUrl,
    long botUserId,
    long dialogId,
    String dialogType,
    long messageId,
    long messageUserId,
    int mention,
    String text,
    String replyText,
    String token,
    Map<String, Object> messageUser,
    String extras,
    String version,
    long timestamp,
    Map<String, Object> member,
    Map<String, Object> operator,
    String groupType,
    String dialogName) {

  public static final String EVENT_MESSAGE = "message";
  public static final String EVENT_DIALOG_OPEN = "dialogOpen";
  public static final String EVENT_MEMBER_JOIN = "memberJoin";
  public static final String EVENT_MEMBER_LEAVE = "memberLeave";
}
