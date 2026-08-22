package com.bluedock.user.socket.web;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.model.ResultModel;
import com.bluedock.common.redis.RedisKeys;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserPresenceController {
  private static final int MAX_IDS = 100;

  private final StringRedisTemplate redis;

  public UserPresenceController(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * 批量查询在线态；{@code userIds} 逗号分隔，最多 100。
   *
   * <p>响应 {@code items:[{userId,online,pcActive}]}。
   */
  @GetMapping("/presence")
  public ResultModel<Map<String, Object>> presence(@RequestParam(required = false) String userIds) {
    AuthContext.requireUserId();
    List<Long> ids = parseIds(userIds);
    if (ids.isEmpty()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.BAD_REQUEST);
    }
    List<Map<String, Object>> items = new ArrayList<>(ids.size());
    for (Long id : ids) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("userId", id);
      boolean online = Boolean.TRUE.equals(redis.hasKey(RedisKeys.online(id)));
      row.put("online", online);
      row.put("pcActive", Boolean.TRUE.equals(redis.hasKey(RedisKeys.pcActive(id))));
      items.add(row);
    }
    return ResultModel.ok(Map.of("items", items));
  }

  static List<Long> parseIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    Set<Long> out = new LinkedHashSet<>();
    for (String part : raw.split(",")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      try {
        long id = Long.parseLong(p);
        if (id > 0) {
          out.add(id);
        }
      } catch (NumberFormatException ignored) {
        // skip
      }
      if (out.size() >= MAX_IDS) {
        break;
      }
    }
    return new ArrayList<>(out);
  }
}
