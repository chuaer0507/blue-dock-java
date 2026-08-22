package com.bluedock.task.archive;

import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 已完成任务自动归档调度（默认每小时）。 */
@Component
public class TaskAutoArchiveScheduler {
  private static final Logger log = LoggerFactory.getLogger(TaskAutoArchiveScheduler.class);

  private final TaskAutoArchiveService service;
  private final StringRedisTemplate redis;

  public TaskAutoArchiveScheduler(TaskAutoArchiveService service, StringRedisTemplate redis) {
    this.service = service;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.task.auto-archive-ms:3600000}")
  public void tick() {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.taskAutoArchiveTick(), "1", Duration.ofMinutes(55));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      service.runOnce();
    } catch (Exception e) {
      log.warn("task auto-archive tick failed: {}", e.toString());
    }
  }
}
