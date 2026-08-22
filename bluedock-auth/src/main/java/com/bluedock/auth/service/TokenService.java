package com.bluedock.auth.service;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.redis.RedisKeys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final StringRedisTemplate redis;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public TokenService(
      StringRedisTemplate redis,
      @Value("${bluedock.jwt.access-ttl-seconds:7200}") long accessTtlSeconds,
      @Value("${bluedock.jwt.refresh-ttl-seconds:2592000}") long refreshTtlSeconds) {
    this.redis = redis;
    this.accessTtl = Duration.ofSeconds(accessTtlSeconds);
    this.refreshTtl = Duration.ofSeconds(refreshTtlSeconds);
  }

  /** 短效 access + 长效 refresh。 */
  public record TokenPair(String accessToken, String refreshToken) {}

  public TokenPair issuePair(long userId) {
    String access = newToken();
    String refresh = newToken();
    storeAccess(access, userId);
    storeRefresh(refresh, userId, access);
    return new TokenPair(access, refresh);
  }

  /**
   * 仅发 access（无 refresh）。测试 / 兼容旧调用；生产登录请用 {@link #issuePair(long)}。
   */
  public String issue(long userId) {
    String access = newToken();
    storeAccess(access, userId);
    return access;
  }

  public Optional<Long> resolve(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    String raw = redis.opsForValue().get(RedisKeys.accessToken(token));
    if (raw == null) {
      return Optional.empty();
    }
    return Optional.of(Long.parseLong(raw));
  }

  /**
   * 用 refresh 轮换一对新 token；旧 access/refresh 一并失效。
   *
   * @throws BusinessException {@link ErrorCodes#TOKEN_EXPIRED} 当 refresh 缺失或已失效
   */
  public TokenPair refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new BusinessException(ErrorCodes.TOKEN_EXPIRED, I18nKeys.UNAUTHORIZED_EXPIRED);
    }
    String payload = redis.opsForValue().get(RedisKeys.refreshToken(refreshToken));
    if (payload == null || payload.isBlank()) {
      throw new BusinessException(ErrorCodes.TOKEN_EXPIRED, I18nKeys.UNAUTHORIZED_EXPIRED);
    }
    String[] parts = payload.split("\\|", 2);
    long userId;
    try {
      userId = Long.parseLong(parts[0]);
    } catch (NumberFormatException e) {
      redis.delete(RedisKeys.refreshToken(refreshToken));
      throw new BusinessException(ErrorCodes.TOKEN_EXPIRED, I18nKeys.UNAUTHORIZED_EXPIRED);
    }
    String oldAccess = parts.length > 1 ? parts[1] : null;
    revokeRefreshBinding(refreshToken, oldAccess);
    return issuePair(userId);
  }

  /** 吊销 access；若存在绑定的 refresh 一并吊销。 */
  public void revoke(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    String refresh = redis.opsForValue().get(RedisKeys.accessToRefresh(token));
    revokeAccessOnly(token);
    if (refresh != null && !refresh.isBlank()) {
      redis.delete(RedisKeys.refreshToken(refresh));
    }
  }

  /**
   * 当前 token 剩余 TTL（秒）。不存在或已过期返回 empty。
   *
   * <p>Redis {@code getExpire}：{@code -2} 键不存在；{@code -1} 无过期（视为配置异常，按 0 处理）。
   */
  public Optional<Long> remainingTtlSeconds(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    Long expire = redis.getExpire(RedisKeys.accessToken(token));
    if (expire == null || expire == -2L) {
      return Optional.empty();
    }
    if (expire < 0) {
      return Optional.of(0L);
    }
    return Optional.of(expire);
  }

  public boolean revokeByHash(String hash) {
    if (hash == null || hash.isBlank()) {
      return false;
    }
    String token = redis.opsForValue().get(RedisKeys.accessTokenHash(hash));
    if (token == null) {
      redis.delete(RedisKeys.accessTokenHash(hash));
      return false;
    }
    revoke(token);
    return true;
  }

  public static String hashOf(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      return token;
    }
  }

  private void storeAccess(String access, long userId) {
    redis.opsForValue().set(RedisKeys.accessToken(access), Long.toString(userId), accessTtl);
    redis.opsForValue().set(RedisKeys.accessTokenHash(hashOf(access)), access, accessTtl);
  }

  private void storeRefresh(String refresh, long userId, String access) {
    redis.opsForValue().set(RedisKeys.refreshToken(refresh), userId + "|" + access, refreshTtl);
    redis.opsForValue().set(RedisKeys.accessToRefresh(access), refresh, refreshTtl);
  }

  private void revokeAccessOnly(String access) {
    redis.delete(RedisKeys.accessToken(access));
    redis.delete(RedisKeys.accessTokenHash(hashOf(access)));
    redis.delete(RedisKeys.accessToRefresh(access));
  }

  private void revokeRefreshBinding(String refresh, String oldAccess) {
    redis.delete(RedisKeys.refreshToken(refresh));
    if (oldAccess != null && !oldAccess.isBlank()) {
      revokeAccessOnly(oldAccess);
    }
  }

  private static String newToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
