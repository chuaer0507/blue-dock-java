package com.bluedock.common.notify;

import java.util.List;
import java.util.Map;

/** Kafka {@code bluedock.notify.send} 载荷。 */
public record NotifySendEvent(
    String eventId,
    String channel,
    List<Long> userIds,
    String title,
    String body,
    Map<String, Object> data) {

  public static final String CHANNEL_EMAIL = "email";
  public static final String CHANNEL_PUSH = "push";
  public static final String CHANNEL_DESKTOP = "desktop";
}
