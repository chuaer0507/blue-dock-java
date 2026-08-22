package com.bluedock.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.web.dto.LoginResult;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 扫码登录：桌面 {@code create} → 手机 {@code confirm}（Bearer）→ 桌面 {@code status} 轮询取新 token。
 *
 * <p>code ≥32；TTL 30s；成功后一次性消费。
 */
@Service
public class QrCodeLoginService {
  public static final int CODE_MIN_LEN = 32;
  public static final Duration TTL = Duration.ofSeconds(30);

  private static final String STATUS_WAITING = "waiting";
  private static final String STATUS_CONFIRMED = "confirmed";
  private static final String STATUS_CONSUMED = "consumed";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final AuthService auth;

  public QrCodeLoginService(
      StringRedisTemplate redis, ObjectMapper objectMapper, AuthService auth) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.auth = auth;
  }

  public Map<String, Object> handle(
      String typeRaw, String codeRaw, String clientIp, String userAgent) {
    String type =
        typeRaw == null || typeRaw.isBlank()
            ? "create"
            : typeRaw.trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case "create", "new" -> create();
      case "confirm", "login" -> confirm(codeRaw);
      case "status", "poll", "check" -> status(codeRaw, clientIp, userAgent);
      default ->
          throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_TYPE_INVALID);
    };
  }

  private Map<String, Object> create() {
    String code = UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> ticket = new LinkedHashMap<>();
    ticket.put("status", STATUS_WAITING);
    ticket.put("userId", 0L);
    write(code, ticket);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("code", code);
    out.put("status", STATUS_WAITING);
    out.put("expire", TTL.getSeconds());
    return out;
  }

  private Map<String, Object> confirm(String codeRaw) {
    if (AuthContext.get() == null) {
      throw new BusinessException(ErrorCodes.UNAUTHORIZED, I18nKeys.AUTH_QR_CODE_AUTH_REQUIRED);
    }
    long userId = AuthContext.requireUserId();
    auth.assertCanLogin(userId);
    String code = requireCode(codeRaw);
    Map<String, Object> ticket = read(code);
    if (ticket == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_EXPIRED);
    }
    String status = String.valueOf(ticket.getOrDefault("status", ""));
    if (STATUS_CONSUMED.equals(status)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_USED);
    }
    if (!STATUS_WAITING.equals(status) && !STATUS_CONFIRMED.equals(status)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_INVALID);
    }
    if (STATUS_CONFIRMED.equals(status)) {
      long existing = ((Number) ticket.get("userId")).longValue();
      if (existing != userId) {
        throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_USED);
      }
      return Map.of("status", STATUS_CONFIRMED, "code", code);
    }
    ticket.put("status", STATUS_CONFIRMED);
    ticket.put("userId", userId);
    writeKeepTtl(code, ticket);
    return Map.of("status", STATUS_CONFIRMED, "code", code);
  }

  private Map<String, Object> status(String codeRaw, String clientIp, String userAgent) {
    String code = requireCode(codeRaw);
    Map<String, Object> ticket = read(code);
    if (ticket == null) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_EXPIRED);
    }
    String status = String.valueOf(ticket.getOrDefault("status", ""));
    if (STATUS_WAITING.equals(status)) {
      return Map.of("status", STATUS_WAITING, "code", code);
    }
    if (STATUS_CONSUMED.equals(status)) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_USED);
    }
    if (STATUS_CONFIRMED.equals(status)) {
      long userId = ((Number) ticket.get("userId")).longValue();
      LoginResult login = auth.loginByUserId(userId, clientIp, userAgent);
      ticket.put("status", STATUS_CONSUMED);
      writeKeepTtl(code, ticket);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("status", "success");
      out.put("code", code);
      out.put("token", login.token());
      out.put("refreshToken", login.refreshToken());
      out.put("user", login.user());
      return out;
    }
    throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_INVALID);
  }

  private String requireCode(String codeRaw) {
    if (codeRaw == null || codeRaw.isBlank()) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_INVALID);
    }
    String code = codeRaw.trim();
    if (code.length() < CODE_MIN_LEN) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_INVALID);
    }
    return code;
  }

  private void write(String code, Map<String, Object> ticket) {
    try {
      redis.opsForValue().set(RedisKeys.qrCode(code), objectMapper.writeValueAsString(ticket), TTL);
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_INVALID);
    }
  }

  private void writeKeepTtl(String code, Map<String, Object> ticket) {
    try {
      Long ttl = redis.getExpire(RedisKeys.qrCode(code));
      String json = objectMapper.writeValueAsString(ticket);
      if (ttl != null && ttl > 0) {
        redis.opsForValue().set(RedisKeys.qrCode(code), json, Duration.ofSeconds(ttl));
      } else {
        redis.opsForValue().set(RedisKeys.qrCode(code), json, Duration.ofSeconds(5));
      }
    } catch (Exception e) {
      throw new BusinessException(ErrorCodes.BAD_REQUEST, I18nKeys.AUTH_QR_CODE_INVALID);
    }
  }

  private Map<String, Object> read(String code) {
    String json = redis.opsForValue().get(RedisKeys.qrCode(code));
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (Exception e) {
      return null;
    }
  }
}
