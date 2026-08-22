package com.bluedock.worker.notify.push;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.worker.notify.channel.AppPushChannel;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 轮询 APP 推送延时队列（到期后复查已读再投递）。 */
@Component
public class AppPushDelayScheduler {
  private static final Logger log = LoggerFactory.getLogger(AppPushDelayScheduler.class);
  private static final Duration TICK_TTL = Duration.ofSeconds(2);

  private final StringRedisTemplate redis;
  private final AppPushDelayQueue delayQueue;
  private final AppPushChannel push;

  public AppPushDelayScheduler(
      StringRedisTemplate redis, AppPushDelayQueue delayQueue, AppPushChannel push) {
    this.redis = redis;
    this.delayQueue = delayQueue;
    this.push = push;
  }

  @Scheduled(fixedDelayString = "${bluedock.app-push.delay-poll-ms:2000}")
  public void poll() {
    Boolean first =
        redis.opsForValue().setIfAbsent(RedisKeys.appPushDelayTick(), "1", TICK_TTL);
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    List<AppPushDelayQueue.DelayedJob> jobs = delayQueue.pollDue(50);
    for (AppPushDelayQueue.DelayedJob job : jobs) {
      try {
        push.deliverAfterDelay(job);
      } catch (Exception e) {
        log.warn("appPush delay deliver fail: {}", e.toString());
      }
    }
  }
}
