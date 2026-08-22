package com.bluedock.messenger.email;

import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 未读消息邮件汇总调度（默认 5 分钟）。 */
@Component
public class UnreadEmailNoticeScheduler {
  private static final Logger log = LoggerFactory.getLogger(UnreadEmailNoticeScheduler.class);

  private final UnreadEmailNoticeService service;
  private final StringRedisTemplate redis;

  public UnreadEmailNoticeScheduler(UnreadEmailNoticeService service, StringRedisTemplate redis) {
    this.service = service;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.email.unread-notice-ms:300000}")
  public void tick() {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.emailUnreadNoticeTick(), "1", Duration.ofMinutes(4));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      service.runOnce();
    } catch (Exception e) {
      log.warn("unread email notice tick failed: {}", e.toString());
    }
  }
}
