package com.bluedock.worker.notify.channel;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.notify.NotifySendEvent;
import com.bluedock.common.notify.NotifySettingNames;
import com.bluedock.common.notify.apppush.AppPushClient;
import com.bluedock.common.notify.apppush.AppPushSendResult;
import com.bluedock.common.notify.apppush.AppPushSettingMaps;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.worker.notify.push.AppPushDelayQueue;
import com.bluedock.worker.notify.repo.AppPushAliasDeliveryRepository;
import com.bluedock.worker.notify.repo.AppPushAliasRow;
import com.bluedock.worker.notify.repo.AppPushLogRepository;
import com.bluedock.worker.notify.repo.DialogMuteCheckRepository;
import com.bluedock.worker.notify.repo.MessageReadCheckRepository;
import com.bluedock.worker.notify.repo.NotifySettingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppPushChannel {
  private static final Logger log = LoggerFactory.getLogger(AppPushChannel.class);
  private static final int PER_PLATFORM_LIMIT = 5;

  private final NotifySettingRepository settings;
  private final AppPushAliasDeliveryRepository aliases;
  private final StringRedisTemplate redis;
  private final AppPushClient appPush;
  private final AppPushDelayQueue delayQueue;
  private final MessageReadCheckRepository reads;
  private final DialogMuteCheckRepository mutes;
  private final AppPushLogRepository logs;

  public AppPushChannel(
      NotifySettingRepository settings,
      AppPushAliasDeliveryRepository aliases,
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      AppPushDelayQueue delayQueue,
      MessageReadCheckRepository reads,
      DialogMuteCheckRepository mutes,
      AppPushLogRepository logs) {
    this.settings = settings;
    this.aliases = aliases;
    this.redis = redis;
    this.appPush = new AppPushClient(objectMapper);
    this.delayQueue = delayQueue;
    this.reads = reads;
    this.mutes = mutes;
    this.logs = logs;
  }

  public void deliver(NotifySendEvent event) {
    Map<String, Object> cfg = loadConfig();
    if (!AppPushSettingMaps.enabled(cfg) || !hasKeys(cfg)) {
      return;
    }
    if (eventSilent(event.data())) {
      skipAll(event.userIds(), event, "silence");
      return;
    }

    List<Long> candidates = filterMuted(event.userIds(), event.data(), event);
    List<Long> immediate = new ArrayList<>();
    List<Long> delayed = new ArrayList<>();
    for (Long userId : candidates) {
      Boolean active = redis.hasKey(RedisKeys.pcActive(userId));
      if (Boolean.TRUE.equals(active)) {
        delayed.add(userId);
      } else {
        immediate.add(userId);
      }
    }

    if (!delayed.isEmpty()) {
      delayQueue.enqueue(event.eventId(), delayed, event.title(), event.body(), event.data());
      long messageId = extractLong(event.data(), "messageId", "message_id");
      long dialogId = extractLong(event.data(), "dialogId", "dialog_id");
      for (Long userId : delayed) {
        logs.insert(
            userId,
            "",
            "",
            event.title(),
            event.body(),
            null,
            null,
            "delayed",
            "pc_active",
            event.eventId(),
            messageId,
            dialogId);
      }
      log.debug("appPush delayed users={}", delayed.size());
    }
    if (!immediate.isEmpty()) {
      sendToUsers(cfg, event.eventId(), immediate, event.title(), event.body(), event.data());
    }
  }

  /** 延时到期后投递；已读 / 免打扰则跳过。 */
  public void deliverAfterDelay(AppPushDelayQueue.DelayedJob job) {
    if (job == null || job.userIds() == null || job.userIds().isEmpty()) {
      return;
    }
    Map<String, Object> cfg = loadConfig();
    if (!AppPushSettingMaps.enabled(cfg) || !hasKeys(cfg)) {
      return;
    }
    if (eventSilent(job.data())) {
      for (Long userId : job.userIds()) {
        if (userId == null) {
          continue;
        }
        logSkip(userId, job, "silence");
      }
      return;
    }
    long messageId = extractLong(job.data(), "messageId", "message_id");
    long dialogId = extractLong(job.data(), "dialogId", "dialog_id");
    Set<Long> muted = mutedSet(dialogId, job.userIds(), job.data());
    List<Long> targets = new ArrayList<>();
    for (Long userId : job.userIds()) {
      if (userId == null) {
        continue;
      }
      if (muted.contains(userId)) {
        logSkip(userId, job, "muted");
        continue;
      }
      if (messageId > 0 && reads.isSilent(messageId, userId) && !mentioned(job.data())) {
        logSkip(userId, job, "muted");
        continue;
      }
      if (messageId > 0 && reads.isRead(messageId, userId)) {
        logSkip(userId, job, "already_read");
        continue;
      }
      targets.add(userId);
    }
    if (targets.isEmpty()) {
      return;
    }
    sendToUsers(cfg, job.eventId(), targets, job.title(), job.body(), job.data());
  }

  private List<Long> filterMuted(
      List<Long> userIds, Map<String, Object> data, NotifySendEvent event) {
    List<Long> out = new ArrayList<>();
    if (userIds == null) {
      return out;
    }
    long dialogId = extractLong(data, "dialogId", "dialog_id");
    long messageId = extractLong(data, "messageId", "message_id");
    Set<Long> muted = mutedSet(dialogId, userIds, data);
    for (Long userId : userIds) {
      if (userId == null) {
        continue;
      }
      if (muted.contains(userId)) {
        logs.insert(
            userId,
            "",
            "",
            event.title(),
            event.body(),
            null,
            null,
            "skipped",
            "muted",
            event.eventId(),
            messageId,
            dialogId);
        continue;
      }
      if (messageId > 0 && reads.isSilent(messageId, userId) && !mentioned(data)) {
        logs.insert(
            userId,
            "",
            "",
            event.title(),
            event.body(),
            null,
            null,
            "skipped",
            "muted",
            event.eventId(),
            messageId,
            dialogId);
        continue;
      }
      out.add(userId);
    }
    return out;
  }

  private Set<Long> mutedSet(long dialogId, List<Long> userIds, Map<String, Object> data) {
    // 单用户事件 mentioned=true：@强制提醒，不再按会话 mute 拦截
    if (dialogId <= 0 || mentioned(data)) {
      return Set.of();
    }
    return mutes.mutedUserIds(dialogId, userIds);
  }

  private void skipAll(List<Long> userIds, NotifySendEvent event, String reason) {
    if (userIds == null) {
      return;
    }
    long messageId = extractLong(event.data(), "messageId", "message_id");
    long dialogId = extractLong(event.data(), "dialogId", "dialog_id");
    for (Long userId : userIds) {
      if (userId == null) {
        continue;
      }
      logs.insert(
          userId,
          "",
          "",
          event.title(),
          event.body(),
          null,
          null,
          "skipped",
          reason,
          event.eventId(),
          messageId,
          dialogId);
    }
  }

  private void logSkip(Long userId, AppPushDelayQueue.DelayedJob job, String reason) {
    long messageId = extractLong(job.data(), "messageId", "message_id");
    long dialogId = extractLong(job.data(), "dialogId", "dialog_id");
    logs.insert(
        userId,
        "",
        "",
        job.title(),
        job.body(),
        null,
        null,
        "skipped",
        reason,
        job.eventId(),
        messageId,
        dialogId);
  }

  private void sendToUsers(
      Map<String, Object> cfg,
      String eventId,
      List<Long> targets,
      String title,
      String body,
      Map<String, Object> data) {
    List<AppPushAliasRow> rows = aliases.listActive(targets);
    Map<String, List<String>> iosByBucket = new HashMap<>();
    Map<String, List<String>> androidByBucket = new HashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    Map<String, List<Long>> iosUsers = new HashMap<>();
    Map<String, List<Long>> androidUsers = new HashMap<>();
    for (AppPushAliasRow row : rows) {
      String key = row.userId() + ":" + row.platform();
      int n = counts.getOrDefault(key, 0);
      if (n >= PER_PLATFORM_LIMIT) {
        continue;
      }
      counts.put(key, n + 1);
      if ("ios".equals(row.platform())) {
        iosByBucket.computeIfAbsent("all", k -> new ArrayList<>()).add(row.alias());
        iosUsers.computeIfAbsent("all", k -> new ArrayList<>()).add(row.userId());
      } else if ("android".equals(row.platform())) {
        androidByBucket.computeIfAbsent("all", k -> new ArrayList<>()).add(row.alias());
        androidUsers.computeIfAbsent("all", k -> new ArrayList<>()).add(row.userId());
      }
    }

    String t = title == null ? "" : title;
    String text = body == null ? "" : body;
    Map<String, Object> extra = data == null ? Map.of() : data;
    Integer badge = null;
    Object badgeObj = extra.get("badge");
    if (badgeObj instanceof Number num) {
      badge = num.intValue();
    }
    String aliasType = AppPushSettingMaps.aliasType(cfg);
    boolean production = AppPushSettingMaps.production(cfg);
    long messageId = extractLong(data, "messageId", "message_id");
    long dialogId = extractLong(data, "dialogId", "dialog_id");

    String iosKey = AppPushSettingMaps.iosKey(cfg);
    String iosSecret = AppPushSettingMaps.iosSecret(cfg);
    List<String> iosAliases = iosByBucket.getOrDefault("all", List.of());
    if (!iosAliases.isEmpty() && !iosKey.isBlank() && !iosSecret.isBlank()) {
      try {
        AppPushSendResult result =
            appPush.sendCustomizedCast(
                iosKey, iosSecret, aliasType, iosAliases, t, text, extra, badge, production, true);
        log.info("appPush ios aliases={} resp={}", iosAliases.size(), truncate(result.responseBody()));
        logs.insertBatch(
            iosUsers.getOrDefault("all", List.of()),
            "ios",
            iosAliases,
            t,
            text,
            result.requestBody(),
            result.responseBody(),
            "sent",
            "",
            eventId,
            messageId,
            dialogId);
      } catch (Exception e) {
        log.warn("appPush ios fail: {}", e.toString());
        logs.insertBatch(
            iosUsers.getOrDefault("all", List.of()),
            "ios",
            iosAliases,
            t,
            text,
            null,
            e.toString(),
            "failed",
            "",
            eventId,
            messageId,
            dialogId);
      }
    }

    String androidKey = AppPushSettingMaps.androidKey(cfg);
    String androidSecret = AppPushSettingMaps.androidSecret(cfg);
    List<String> androidAliases = androidByBucket.getOrDefault("all", List.of());
    if (!androidAliases.isEmpty() && !androidKey.isBlank() && !androidSecret.isBlank()) {
      try {
        AppPushSendResult result =
            appPush.sendCustomizedCast(
                androidKey,
                androidSecret,
                aliasType,
                androidAliases,
                t,
                text,
                extra,
                badge,
                production,
                false);
        log.info(
            "appPush android aliases={} resp={}",
            androidAliases.size(),
            truncate(result.responseBody()));
        logs.insertBatch(
            androidUsers.getOrDefault("all", List.of()),
            "android",
            androidAliases,
            t,
            text,
            result.requestBody(),
            result.responseBody(),
            "sent",
            "",
            eventId,
            messageId,
            dialogId);
      } catch (Exception e) {
        log.warn("appPush android fail: {}", e.toString());
        logs.insertBatch(
            androidUsers.getOrDefault("all", List.of()),
            "android",
            androidAliases,
            t,
            text,
            null,
            e.toString(),
            "failed",
            "",
            eventId,
            messageId,
            dialogId);
      }
    }
  }

  private Map<String, Object> loadConfig() {
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("open", "close");
    defaults.put("iosKey", "");
    defaults.put("iosSecret", "");
    defaults.put("androidKey", "");
    defaults.put("androidSecret", "");
    defaults.put("aliasType", "bluedock");
    defaults.put("productionMode", "true");
    Map<String, Object> cfg = settings.load(NotifySettingNames.APP_PUSH, defaults);
    if (!AppPushSettingMaps.enabled(cfg)) {
      log.debug("push skip: appPush closed");
    } else if (!hasKeys(cfg)) {
      log.debug("push skip: no appPush keys");
    }
    return cfg;
  }

  private static boolean hasKeys(Map<String, Object> cfg) {
    String iosKey = AppPushSettingMaps.iosKey(cfg);
    String iosSecret = AppPushSettingMaps.iosSecret(cfg);
    String androidKey = AppPushSettingMaps.androidKey(cfg);
    String androidSecret = AppPushSettingMaps.androidSecret(cfg);
    return (!iosKey.isBlank() && !iosSecret.isBlank())
        || (!androidKey.isBlank() && !androidSecret.isBlank());
  }

  static boolean eventSilent(Map<String, Object> data) {
    if (data == null) {
      return false;
    }
    Object v = data.get("isSilent");
    if (v == null) {
      v = data.get("silence");
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v != null) {
      String s = String.valueOf(v).trim();
      return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }
    return false;
  }

  static boolean mentioned(Map<String, Object> data) {
    if (data == null) {
      return false;
    }
    Object v = data.get("mentioned");
    if (v instanceof Boolean b) {
      return b;
    }
    if (v != null) {
      String s = String.valueOf(v).trim();
      return "1".equals(s) || "true".equalsIgnoreCase(s);
    }
    return false;
  }

  static long extractLong(Map<String, Object> data, String... keys) {
    if (data == null) {
      return 0L;
    }
    for (String k : keys) {
      Object v = data.get(k);
      if (v instanceof Number n) {
        return n.longValue();
      }
      if (v != null) {
        try {
          return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException ignored) {
          // next
        }
      }
    }
    return 0L;
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() <= 200 ? s : s.substring(0, 200);
  }
}
