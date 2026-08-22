package com.bluedock.task.remind;

import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 未领取任务提醒调度（默认每分钟）。 */
@Component
public class UnclaimedTaskRemindScheduler {
  private static final Logger log = LoggerFactory.getLogger(UnclaimedTaskRemindScheduler.class);

  private final UnclaimedTaskRemindService service;
  private final StringRedisTemplate redis;

  public UnclaimedTaskRemindScheduler(
      UnclaimedTaskRemindService service, StringRedisTemplate redis) {
    this.service = service;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.task.unclaimed-remind-ms:60000}")
  public void tick() {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.unclaimedTaskRemindTick(), "1", Duration.ofSeconds(50));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      service.runOnce();
    } catch (Exception e) {
      log.warn("unclaimed task remind tick failed: {}", e.toString());
    }
  }
}
