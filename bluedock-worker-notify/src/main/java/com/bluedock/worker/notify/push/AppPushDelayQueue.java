package com.bluedock.worker.notify.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.common.redis.RedisKeys;
import com.bluedock.common.util.IdGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

/**
 * PC 在线 APP 推送 10 秒延时调度（Redis ZSET score=到期毫秒）。
 *
 * <p>仅作 Worker 内延时调度，不是跨域业务 MQ。
 */
@Component
public class AppPushDelayQueue {
  private static final Logger log = LoggerFactory.getLogger(AppPushDelayQueue.class);
  public static final long DELAY_MS = 10_000L;
  private static final Duration JOB_TTL = Duration.ofHours(1);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public AppPushDelayQueue(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public void enqueue(
      String eventId,
      List<Long> userIds,
      String title,
      String body,
      Map<String, Object> data) {
    if (userIds == null || userIds.isEmpty()) {
      return;
    }
    String jobId = Long.toUnsignedString(IdGenerator.nextId());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId == null ? "" : eventId);
    payload.put("userIds", userIds);
    payload.put("title", title == null ? "" : title);
    payload.put("body", body == null ? "" : body);
    payload.put("data", data == null ? Map.of() : data);
    try {
      String json = objectMapper.writeValueAsString(payload);
      long due = System.currentTimeMillis() + DELAY_MS;
      redis.opsForValue().set(RedisKeys.appPushDelayJob(jobId), json, JOB_TTL);
      redis.opsForZSet().add(RedisKeys.appPushDelayQueue(), jobId, due);
      log.debug("appPush delay enqueue jobId={} users={} due={}", jobId, userIds.size(), due);
    } catch (Exception e) {
      log.warn("appPush delay enqueue fail: {}", e.toString());
    }
  }

  public List<DelayedJob> pollDue(int limit) {
    long now = System.currentTimeMillis();
    Set<ZSetOperations.TypedTuple<String>> due =
        redis
            .opsForZSet()
            .rangeByScoreWithScores(RedisKeys.appPushDelayQueue(), 0, now, 0, Math.max(1, limit));
    if (due == null || due.isEmpty()) {
      return List.of();
    }
    List<DelayedJob> out = new ArrayList<>();
    for (ZSetOperations.TypedTuple<String> tuple : due) {
      String jobId = tuple.getValue();
      if (jobId == null || jobId.isBlank()) {
        continue;
      }
      Long removed = redis.opsForZSet().remove(RedisKeys.appPushDelayQueue(), jobId);
      if (removed == null || removed == 0) {
        continue;
      }
      String json = redis.opsForValue().get(RedisKeys.appPushDelayJob(jobId));
      redis.delete(RedisKeys.appPushDelayJob(jobId));
      if (json == null || json.isBlank()) {
        continue;
      }
      try {
        Map<String, Object> map = objectMapper.readValue(json, MAP);
        out.add(DelayedJob.from(map));
      } catch (Exception e) {
        log.warn("appPush delay job parse fail jobId={}: {}", jobId, e.toString());
      }
    }
    return out;
  }

  public record DelayedJob(
      String eventId, List<Long> userIds, String title, String body, Map<String, Object> data) {
    static DelayedJob from(Map<String, Object> map) {
      List<Long> users = new ArrayList<>();
      Object raw = map.get("userIds");
      if (raw instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Number n) {
            users.add(n.longValue());
          } else if (o != null) {
            try {
              users.add(Long.parseLong(String.valueOf(o)));
            } catch (NumberFormatException ignored) {
              // skip
            }
          }
        }
      }
      Map<String, Object> data = Map.of();
      Object d = map.get("data");
      if (d instanceof Map<?, ?> m) {
        Map<String, Object> copy = new LinkedHashMap<>();
        m.forEach((k, v) -> copy.put(String.valueOf(k), v));
        data = copy;
      }
      return new DelayedJob(
          String.valueOf(map.getOrDefault("eventId", "")),
          users,
          String.valueOf(map.getOrDefault("title", "")),
          String.valueOf(map.getOrDefault("body", "")),
          data);
    }
  }
}
