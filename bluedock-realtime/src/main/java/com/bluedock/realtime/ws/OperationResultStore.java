package com.bluedock.realtime.ws;

import tools.jackson.databind.ObjectMapper;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 将 WS {@code operationResult} 回包写入 Redis，供 assistant/operation/result 轮询。 */
@Component
public class OperationResultStore {
  private static final Logger log = LoggerFactory.getLogger(OperationResultStore.class);
  private static final Duration TTL = Duration.ofSeconds(60);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public OperationResultStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public void save(long userId, String requestId, boolean success, Object result, String error) {
    if (requestId == null || requestId.isBlank()) {
      return;
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("userId", userId);
    row.put("success", success);
    row.put("result", result);
    row.put("error", error);
    try {
      redis
          .opsForValue()
          .set(RedisKeys.assistantOp(requestId.trim()), objectMapper.writeValueAsString(row), TTL);
    } catch (Exception e) {
      log.warn("operationResult store failed requestId={}: {}", requestId, e.toString());
    }
  }
}
