package com.bluedock.user.socket.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.bluedock.auth.security.AuthContext;
import com.bluedock.auth.security.AuthUser;
import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.redis.RedisKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UserPresenceControllerTest {
  @Mock StringRedisTemplate redis;

  UserPresenceController controller;

  @BeforeEach
  void setUp() {
    AuthContext.set(new AuthUser(1L));
    controller = new UserPresenceController(redis);
  }

  @AfterEach
  void clear() {
    AuthContext.clear();
  }

  @Test
  void parseIds() {
    assertEquals(List.of(1L, 2L, 9L), UserPresenceController.parseIds("1,2, 9,2"));
    assertTrue(UserPresenceController.parseIds("").isEmpty());
  }

  @Test
  void presence_returnsFlags() {
    when(redis.hasKey(RedisKeys.online(2L))).thenReturn(true);
    when(redis.hasKey(RedisKeys.pcActive(2L))).thenReturn(true);
    when(redis.hasKey(RedisKeys.online(3L))).thenReturn(false);
    when(redis.hasKey(RedisKeys.pcActive(3L))).thenReturn(false);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items =
        (List<Map<String, Object>>) controller.presence("2,3").data().get("items");
    assertEquals(2, items.size());
    assertEquals(2L, items.get(0).get("userId"));
    assertEquals(true, items.get(0).get("online"));
    assertEquals(true, items.get(0).get("pcActive"));
    assertEquals(false, items.get(1).get("online"));
  }

  @Test
  void presence_emptyIds_badRequest() {
    assertThrows(BusinessException.class, () -> controller.presence(""));
  }
}
