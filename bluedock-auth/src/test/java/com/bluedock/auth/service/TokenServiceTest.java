package com.bluedock.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.redis.RedisKeys;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenServiceTest {
  @Mock
  StringRedisTemplate redis;
  @Mock
  ValueOperations<String, String> values;
  TokenService tokens;

  @BeforeEach
  void setUp() {
    when(redis.opsForValue()).thenReturn(values);
    tokens = new TokenService(redis, 3600, 86400);
  }

  @Test
  void remainingTtlSeconds_ok() {
    when(redis.getExpire(RedisKeys.accessToken("abc"))).thenReturn(120L);
    assertEquals(Optional.of(120L), tokens.remainingTtlSeconds("abc"));
  }

  @Test
  void remainingTtlSeconds_missing() {
    when(redis.getExpire(RedisKeys.accessToken("gone"))).thenReturn(-2L);
    assertTrue(tokens.remainingTtlSeconds("gone").isEmpty());
  }

  @Test
  void issue_setsTtl() {
    String token = tokens.issue(9L);
    assertEquals(32, token.length());
    verify(values).set(eq(RedisKeys.accessToken(token)), eq("9"), eq(Duration.ofSeconds(3600)));
    verify(values).set(anyString(), eq(token), eq(Duration.ofSeconds(3600)));
  }

  @Test
  void issuePair_storesAccessAndRefresh() {
    TokenService.TokenPair pair = tokens.issuePair(9L);
    assertEquals(32, pair.accessToken().length());
    assertEquals(32, pair.refreshToken().length());
    assertNotEquals(pair.accessToken(), pair.refreshToken());
    verify(values)
        .set(eq(RedisKeys.accessToken(pair.accessToken())), eq("9"), eq(Duration.ofSeconds(3600)));
    verify(values)
        .set(
            eq(RedisKeys.refreshToken(pair.refreshToken())),
            eq("9|" + pair.accessToken()),
            eq(Duration.ofSeconds(86400)));
    verify(values)
        .set(
            eq(RedisKeys.accessToRefresh(pair.accessToken())),
            eq(pair.refreshToken()),
            eq(Duration.ofSeconds(86400)));
  }

  @Test
  void refresh_rotatesPair() {
    when(values.get(RedisKeys.refreshToken("old-rt"))).thenReturn("9|old-at");
    TokenService.TokenPair pair = tokens.refresh("old-rt");
    assertEquals(32, pair.accessToken().length());
    verify(redis).delete(RedisKeys.refreshToken("old-rt"));
    verify(redis, atLeastOnce()).delete(RedisKeys.accessToken("old-at"));
    ArgumentCaptor<String> refreshKey = ArgumentCaptor.forClass(String.class);
    verify(values, atLeastOnce())
        .set(refreshKey.capture(), anyString(), eq(Duration.ofSeconds(86400)));
    assertTrue(refreshKey.getAllValues().stream().anyMatch(k -> k.contains(":auth:refresh:")));
  }

  @Test
  void refresh_missing_throwsTokenExpired() {
    when(values.get(RedisKeys.refreshToken("gone"))).thenReturn(null);
    BusinessException ex = assertThrows(BusinessException.class, () -> tokens.refresh("gone"));
    assertEquals(ErrorCodes.TOKEN_EXPIRED, ex.getCode());
  }
}
