package com.bluedock.messenger.todo;

import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 消息待办到期提醒调度（默认每分钟）。 */
@Component
public class DialogTodoRemindScheduler {
  private static final Logger log = LoggerFactory.getLogger(DialogTodoRemindScheduler.class);

  private final DialogTodoRemindService service;
  private final StringRedisTemplate redis;

  public DialogTodoRemindScheduler(DialogTodoRemindService service, StringRedisTemplate redis) {
    this.service = service;
    this.redis = redis;
  }

  @Scheduled(fixedDelayString = "${bluedock.dialog.todo-remind-ms:60000}")
  public void tick() {
    Boolean first =
        redis
            .opsForValue()
            .setIfAbsent(RedisKeys.dialogTodoRemindTick(), "1", Duration.ofSeconds(50));
    if (Boolean.FALSE.equals(first)) {
      return;
    }
    try {
      service.runOnce();
    } catch (Exception e) {
      log.warn("dialog todo-remind tick failed: {}", e.toString());
    }
  }
}
