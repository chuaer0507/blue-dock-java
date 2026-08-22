package com.bluedock.messenger.bot;

import com.bluedock.messenger.repo.DialogRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 按自建机器人 {@code clearDay} 软删历史消息，并推进 {@code clear_at} 水位。
 */
@Service
public class UserBotClearDayService {
  private static final Logger log = LoggerFactory.getLogger(UserBotClearDayService.class);
  private static final int BOT_BATCH = 50;
  private static final int MSG_BATCH = 1000;
  private static final int MAX_ROUNDS = 20;

  private final DialogRepository dialogs;

  public UserBotClearDayService(DialogRepository dialogs) {
    this.dialogs = dialogs;
  }

  public Map<String, Object> runOnce() {
    return runAt(LocalDateTime.now());
  }

  public Map<String, Object> runAt(LocalDateTime now) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> bots = dialogs.listUserBotsForClear(now, BOT_BATCH);
    int botsProcessed = 0;
    int messagesDeleted = 0;
    for (Map<String, Object> bot : bots) {
      long id = ((Number) bot.get("id")).longValue();
      long botId = ((Number) bot.get("botId")).longValue();
      int clearDay = ((Number) bot.get("clearDay")).intValue();
      if (botId <= 0 || clearDay <= 0) {
        continue;
      }
      clearDay = Math.min(999, Math.max(1, clearDay));
      LocalDateTime before = now.minusDays(clearDay);
      int deleted = 0;
      try {
        for (int round = 0; round < MAX_ROUNDS; round++) {
          int n = dialogs.softDeleteBotMessagesBefore(botId, before, MSG_BATCH);
          deleted += n;
          if (n < MSG_BATCH) {
            break;
          }
        }
        dialogs.updateUserBotClearAt(id, now.plusDays(clearDay));
        botsProcessed++;
        messagesDeleted += deleted;
      } catch (Exception e) {
        log.warn("userBot clearDay botId={} failed: {}", botId, e.toString());
      }
    }
    out.put("bots", bots.size());
    out.put("botsProcessed", botsProcessed);
    out.put("messagesDeleted", messagesDeleted);
    return out;
  }
}
