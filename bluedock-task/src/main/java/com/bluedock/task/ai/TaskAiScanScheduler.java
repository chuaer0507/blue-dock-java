package com.bluedock.task.ai;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.task.service.TaskAiService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 任务 AI 自动扫描（默认每分钟；创建延迟 10s，近 1 天，每批 5）。 */
@Component
public class TaskAiScanScheduler {
  private static final Logger log = LoggerFactory.getLogger(TaskAiScanScheduler.class);

  private final TaskAiService ai;
  private final StringRedisTemplate redis;

  public TaskAiScanScheduler(TaskAiService ai, StringRedisTemplate redis) {
    this.ai = ai;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.task.ai-scan-ms:60000}")
  public void tick() {
    Boolean first =
        redis.opsForValue().setIfAbsent(RedisKeys.taskAiScanTick(), "1", Duration.ofSeconds(50));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      ai.scanPending(10, 1, 5);
    } catch (Exception e) {
      log.warn("task ai scan tick failed: {}", e.toString());
    }
  }
}
