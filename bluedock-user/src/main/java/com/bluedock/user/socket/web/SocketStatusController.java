package com.bluedock.user.socket.web;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.model.ResultModel;
import com.bluedock.common.redis.RedisKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/socket")
public class SocketStatusController {
  private final StringRedisTemplate redis;

  public SocketStatusController(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @GetMapping("/status")
  public ResultModel<Map<String, Object>> status(@RequestParam(required = false) String fd) {
    long userId = AuthContext.requireUserId();
    if (fd == null || fd.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.SOCKET_OFFLINE);
    }
    String owner = redis.opsForValue().get(RedisKeys.wsSession(fd.trim()));
    if (owner == null || !Long.toString(userId).equals(owner)) {
      throw new BusinessException(ErrorCodes.NOT_FOUND, I18nKeys.SOCKET_OFFLINE);
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("fd", fd.trim());
    row.put("userId", userId);
    row.put("online", true);
    return ResultModel.ok(row);
  }
}
