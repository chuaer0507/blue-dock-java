---
description: Redis Key 规范、TTL、数据结构
globs: "**/redis/**,**/*Redis*.java,**/*Cache*.java"
alwaysApply: false
---

# Redis 规则

详见 [docs/data/redis.md](../../docs/data/redis.md)。异步事件走 Kafka，Redis 不做消息队列。

## Key 规范

| 类型 | 模式 |
| ---- | ---- |
| **业务** | `bluedock:{domain}:{id}` |
| **用户会话** | `bluedock:auth:...`、`bluedock:online:{userId}` |
| **锁** | `bluedock:lock:{resource}:{id}` |

## 常用 Key（示意）

| 业务 | Key 模式 | TTL |
| ---- | -------- | --- |
| 验证码 | `bluedock:auth:captcha:{id}` | 5 min |
| Token 黑名单 | `bluedock:auth:blacklist:{jti}` | 至过期 |
| 在线状态 | `bluedock:online:{userId}` | 心跳续期 |
| 限流 | `bluedock:rate:{userId}:{action}` | 窗口长度 |
| 分布式锁 | `bluedock:lock:project:{id}` | 短 TTL |

## 常量集中管理

Key 模式定义在 `bluedock-common` 的 **`RedisKeys`**；禁止硬编码。

## 反模式

```java
// ❌ 硬编码 Key
redisTemplate.opsForValue().set("online:" + userId, ...);

// ❌ 用 Redis List 做业务事件队列（应走 Kafka）
redisTemplate.opsForList().leftPush("events", ...);

// ✅
redisTemplate.opsForValue().set(RedisKeys.online(userId), ...);
```
