package com.bluedock.user.attendance.service;

import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 签到提醒调度（默认每分钟）。 */
@Component
public class AttendanceRemindScheduler {
  private static final Logger log = LoggerFactory.getLogger(AttendanceRemindScheduler.class);

  private final AttendanceRemindService service;
  private final StringRedisTemplate redis;

  public AttendanceRemindScheduler(AttendanceRemindService service, StringRedisTemplate redis) {
    this.service = service;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.attendance.remind-ms:60000}")
  public void tick() {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.attendanceRemindTick(), "1", Duration.ofSeconds(50));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      service.runOnce();
    } catch (Exception e) {
      log.warn("attendance remind tick failed: {}", e.toString());
    }
  }
}
