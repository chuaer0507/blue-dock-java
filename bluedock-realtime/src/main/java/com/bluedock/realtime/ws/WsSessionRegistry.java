package com.bluedock.realtime.ws;

import com.bluedock.common.redis.RedisKeys;
import com.bluedock.realtime.presence.PresenceFanoutService;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** 本机 WebSocket 会话注册表；跨实例扇出走 Kafka。顺带维护 Redis 在线 / PC 活跃 presence。 */
@Component
public class WsSessionRegistry {
  /** 任意端在线标记 TTL；ping 续期。 */
  static final Duration ONLINE_TTL = Duration.ofSeconds(90);
  /** 桌面端活跃标记 TTL；供 APP 推送延时判断。 */
  static final Duration PC_ACTIVE_TTL = Duration.ofSeconds(60);

  private final Map<Long, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();
  private final Map<String, Long> sessionUser = new ConcurrentHashMap<>();
  private final Map<String, String> sessionClient = new ConcurrentHashMap<>();
  private final StringRedisTemplate redis;
  private final PresenceFanoutService presence;

  public WsSessionRegistry(StringRedisTemplate redis, PresenceFanoutService presence) {
    this.redis = redis;
    this.presence = presence;
  }

  public void register(long userId, WebSocketSession session) {
    register(userId, session, null);
  }

  public void register(long userId, WebSocketSession session, String client) {
    String normalized = normalizeClient(client);
    Long sizeBefore = redis.opsForSet().size(RedisKeys.wsUser(userId));
    byUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    sessionUser.put(session.getId(), userId);
    if (normalized != null) {
      sessionClient.put(session.getId(), normalized);
    } else {
      sessionClient.remove(session.getId());
    }
    redis.opsForSet().add(RedisKeys.wsUser(userId), session.getId());
    redis.opsForValue().set(RedisKeys.wsSession(session.getId()), Long.toString(userId));
    touchPresence(userId, session.getId());
    if (sizeBefore == null || sizeBefore == 0L) {
      presence.publishOnline(userId);
    }
  }

  /** 心跳续期在线 / 桌面活跃标记。 */
  public void touchPresence(WebSocketSession session) {
    Long userId = sessionUser.get(session.getId());
    if (userId == null) {
      return;
    }
    touchPresence(userId, session.getId());
  }

  public void unregister(WebSocketSession session) {
    Long userId = sessionUser.remove(session.getId());
    sessionClient.remove(session.getId());
    if (userId == null) {
      return;
    }
    Set<WebSocketSession> set = byUser.get(userId);
    if (set != null) {
      set.remove(session);
      if (set.isEmpty()) {
        byUser.remove(userId);
      }
    }
    redis.opsForSet().remove(RedisKeys.wsUser(userId), session.getId());
    redis.delete(RedisKeys.wsSession(session.getId()));
    Long remaining = redis.opsForSet().size(RedisKeys.wsUser(userId));
    if (remaining == null || remaining == 0) {
      redis.delete(RedisKeys.online(userId));
      redis.delete(RedisKeys.pcActive(userId));
      presence.publishOffline(userId);
    } else if (!hasDesktopSession(userId)) {
      redis.delete(RedisKeys.pcActive(userId));
    }
  }

  public Long userIdOf(WebSocketSession session) {
    return sessionUser.get(session.getId());
  }

  public int pushToUser(long userId, String json) {
    Set<WebSocketSession> set = byUser.get(userId);
    if (set == null || set.isEmpty()) {
      return 0;
    }
    int n = 0;
    TextMessage msg = new TextMessage(json);
    for (WebSocketSession session : set) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        synchronized (session) {
          session.sendMessage(msg);
        }
        n++;
      } catch (IOException ignored) {
        // drop broken session; close callback will unregister
      }
    }
    return n;
  }

  static boolean isDesktopClient(String client) {
    if (client == null || client.isBlank()) {
      return false;
    }
    return switch (client) {
      case "desktop", "electron", "mac", "macos", "windows", "win", "linux", "pc" -> true;
      default -> false;
    };
  }

  static String normalizeClient(String client) {
    if (client == null || client.isBlank()) {
      return null;
    }
    return client.trim().toLowerCase(Locale.ROOT);
  }

  private void touchPresence(long userId, String sessionId) {
    redis.opsForValue().set(RedisKeys.online(userId), "1", ONLINE_TTL);
    String client = sessionClient.get(sessionId);
    if (isDesktopClient(client) || hasDesktopSession(userId)) {
      redis.opsForValue().set(RedisKeys.pcActive(userId), "1", PC_ACTIVE_TTL);
    }
  }

  private boolean hasDesktopSession(long userId) {
    Set<WebSocketSession> set = byUser.get(userId);
    if (set == null || set.isEmpty()) {
      return false;
    }
    for (WebSocketSession s : set) {
      if (isDesktopClient(sessionClient.get(s.getId()))) {
        return true;
      }
    }
    return false;
  }
}
