package com.bluedock.worker.notify.handler;

import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.worker.notify.channel.EmailNotifyChannel;
import com.bluedock.worker.notify.channel.AppPushChannel;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 通知投递：幂等去重后按 channel 路由到 SMTP / APP 推送。 desktop 由客户端本地 Notification 处理，此处仅打日志。
 */
@Component
public class NotifySendHandler {
  private static final Logger log = LoggerFactory.getLogger(NotifySendHandler.class);

  private final StringRedisTemplate redis;
  private final EmailNotifyChannel email;
  private final AppPushChannel push;

  public NotifySendHandler(
      StringRedisTemplate redis, EmailNotifyChannel email, AppPushChannel push) {
    this.redis = redis;
    this.email = email;
    this.push = push;
  }

  public void handle(NotifySendEvent event) {
    if (event == null) {
      return;
    }
    if (event.eventId() != null && !event.eventId().isBlank()) {
      Boolean first =
          redis
              .opsForValue()
              .setIfAbsent(RedisKeys.notifyIdempotency(event.eventId()), "1", Duration.ofDays(2));
      if (Boolean.FALSE.equals(first)) {
        log.debug("notify idempotency skip eventId={}", event.eventId());
        return;
      }
    }
    String channel = event.channel() == null ? "" : event.channel().trim().toLowerCase();
    int n = event.userIds() == null ? 0 : event.userIds().size();
    log.info("notify channel={} users={} title={}", channel, n, event.title() == null ? "" : event.title());
    switch (channel) {
      case NotifySendEvent.CHANNEL_EMAIL -> email.deliver(event);
      case NotifySendEvent.CHANNEL_PUSH -> push.deliver(event);
      case NotifySendEvent.CHANNEL_DESKTOP ->
          log.debug("desktop notify is client-local; skip server delivery");
      default -> log.warn("notify unknown channel={}", channel);
    }
  }
}
