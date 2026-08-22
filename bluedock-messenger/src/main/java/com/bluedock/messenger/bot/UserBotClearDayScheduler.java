package com.bluedock.messenger.bot;

import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 机器人 clearDay 消息清理调度（默认每小时）。 */
@Component
public class UserBotClearDayScheduler {
  private static final Logger log = LoggerFactory.getLogger(UserBotClearDayScheduler.class);

  private final UserBotClearDayService service;
  private final StringRedisTemplate redis;

  public UserBotClearDayScheduler(UserBotClearDayService service, StringRedisTemplate redis) {
    this.service = service;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.userBot.clear-day-ms:3600000}")
  public void tick() {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.userBotClearDayTick(), "1", Duration.ofMinutes(55));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      service.runOnce();
    } catch (Exception e) {
      log.warn("userBot clearDay tick failed: {}", e.toString());
    }
  }
}
