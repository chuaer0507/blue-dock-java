package com.bluedock.messenger.todo;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.todo.TodoAlertRemindBridge;
import com.bluedock.messenger.repo.DialogRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 消息待办 {@code remindAt} 到期 → todo-alert 机器人私聊。 */
@Service
public class DialogTodoRemindService {
  private static final Logger log = LoggerFactory.getLogger(DialogTodoRemindService.class);
  private static final Duration SENT_TTL = Duration.ofDays(2);
  private static final int BATCH = 100;
  private static final String MESSAGE = "你有一条消息待办已到期，请及时处理";

  private final DialogRepository dialogs;
  private final StringRedisTemplate redis;
  private final ObjectProvider<TodoAlertRemindBridge> bridge;

  public DialogTodoRemindService(
      DialogRepository dialogs,
      StringRedisTemplate redis,
      ObjectProvider<TodoAlertRemindBridge> bridge) {
    this.dialogs = dialogs;
    this.redis = redis;
    this.bridge = bridge;
  }

  public Map<String, Object> runOnce() {
    return runAt(LocalDateTime.now());
  }

  public Map<String, Object> runAt(LocalDateTime now) {
    Map<String, Object> out = new LinkedHashMap<>();
    TodoAlertRemindBridge bot = bridge.getIfAvailable();
    if (bot == null) {
      out.put("skipped", true);
      out.put("reason", "noBridge");
      return out;
    }
    List<Map<String, Object>> due = dialogs.listDueTodos(now, BATCH);
    int sent = 0;
    int skipped = 0;
    for (Map<String, Object> row : due) {
      long todoId = ((Number) row.get("id")).longValue();
      long userId = ((Number) row.get("userId")).longValue();
      Boolean first =
          redis
              .opsForValue()
              .setIfAbsent(RedisKeys.dialogTodoRemindSent(todoId), "1", SENT_TTL);
      if (Boolean.FALSE.equals(first)) {
        skipped++;
        continue;
      }
      try {
        long msgId = bot.sendDm(userId, MESSAGE);
        if (msgId > 0) {
          sent++;
        } else {
          skipped++;
          redis.delete(RedisKeys.dialogTodoRemindSent(todoId));
        }
      } catch (Exception e) {
        log.warn("todo remind {} -> {} failed: {}", todoId, userId, e.toString());
        redis.delete(RedisKeys.dialogTodoRemindSent(todoId));
        skipped++;
      }
    }
    out.put("due", due.size());
    out.put("sent", sent);
    out.put("skipped", skipped);
    return out;
  }
}
