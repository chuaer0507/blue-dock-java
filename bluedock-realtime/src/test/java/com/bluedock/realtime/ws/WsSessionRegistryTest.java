package com.bluedock.realtime.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.realtime.presence.PresenceFanoutService;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class WsSessionRegistryTest {
  @Mock StringRedisTemplate redis;
  @Mock SetOperations<String, String> setOps;
  @Mock ValueOperations<String, String> valueOps;
  @Mock PresenceFanoutService presence;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(redis.opsForSet()).thenReturn(setOps);
    org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
  }

  @Test
  void register_desktop_setsOnlineAndPcActive() {
    when(setOps.size(RedisKeys.wsUser(7L))).thenReturn(0L);
    WsSessionRegistry registry = new WsSessionRegistry(redis, presence);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("s1");

    registry.register(7L, session, "desktop");

    verify(setOps).add(eq(RedisKeys.wsUser(7L)), eq("s1"));
    verify(valueOps).set(eq(RedisKeys.wsSession("s1")), eq("7"));
    verify(valueOps).set(eq(RedisKeys.online(7L)), eq("1"), eq(WsSessionRegistry.ONLINE_TTL));
    verify(valueOps).set(eq(RedisKeys.pcActive(7L)), eq("1"), eq(WsSessionRegistry.PC_ACTIVE_TTL));
    verify(presence).publishOnline(7L);
  }

  @Test
  void register_web_setsOnlineOnly() {
    when(setOps.size(RedisKeys.wsUser(7L))).thenReturn(1L);
    WsSessionRegistry registry = new WsSessionRegistry(redis, presence);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("s1");

    registry.register(7L, session, "web");

    verify(valueOps).set(eq(RedisKeys.online(7L)), eq("1"), eq(WsSessionRegistry.ONLINE_TTL));
    verify(valueOps, never())
        .set(eq(RedisKeys.pcActive(7L)), eq("1"), eq(WsSessionRegistry.PC_ACTIVE_TTL));
    verify(presence, never()).publishOnline(7L);
  }

  @Test
  void touchPresence_renewsTtl() {
    when(setOps.size(RedisKeys.wsUser(7L))).thenReturn(0L);
    WsSessionRegistry registry = new WsSessionRegistry(redis, presence);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("s1");
    registry.register(7L, session, "electron");

    registry.touchPresence(session);

    verify(valueOps, org.mockito.Mockito.atLeast(2))
        .set(eq(RedisKeys.online(7L)), eq("1"), eq(WsSessionRegistry.ONLINE_TTL));
    verify(valueOps, org.mockito.Mockito.atLeast(2))
        .set(eq(RedisKeys.pcActive(7L)), eq("1"), eq(WsSessionRegistry.PC_ACTIVE_TTL));
  }

  @Test
  void unregister_lastSession_clearsPresenceAndPublishesOffline() {
    when(setOps.size(RedisKeys.wsUser(7L))).thenReturn(0L);
    WsSessionRegistry registry = new WsSessionRegistry(redis, presence);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("s1");
    registry.register(7L, session, "desktop");
    when(setOps.size(RedisKeys.wsUser(7L))).thenReturn(0L);

    registry.unregister(session);

    verify(redis).delete(RedisKeys.online(7L));
    verify(redis).delete(RedisKeys.pcActive(7L));
    verify(presence).publishOffline(7L);
  }

  @Test
  void pushToUser_sends() throws Exception {
    when(setOps.size(RedisKeys.wsUser(7L))).thenReturn(0L);
    WsSessionRegistry registry = new WsSessionRegistry(redis, presence);
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("s1");
    when(session.isOpen()).thenReturn(true);

    registry.register(7L, session, "web");

    AtomicInteger sent = new AtomicInteger();
    org.mockito.Mockito.doAnswer(
            inv -> {
              sent.incrementAndGet();
              return null;
            })
        .when(session)
        .sendMessage(any(TextMessage.class));

    assertEquals(1, registry.pushToUser(7L, "{\"type\":\"ping\"}"));
    assertEquals(1, sent.get());
  }

  @Test
  void isDesktopClient_recognizesAliases() {
    assertTrue(WsSessionRegistry.isDesktopClient("desktop"));
    assertTrue(WsSessionRegistry.isDesktopClient("electron"));
    assertTrue(WsSessionRegistry.isDesktopClient("mac"));
    assertFalse(WsSessionRegistry.isDesktopClient("web"));
    assertFalse(WsSessionRegistry.isDesktopClient("ios"));
    assertFalse(WsSessionRegistry.isDesktopClient(null));
  }
}
