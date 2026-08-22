---
description: Kafka 消息队列 — Topic、幂等、禁止 Redis 当 MQ
globs: "**/kafka/**,**/*Consumer*.java,**/*Worker*.java,**/messaging/**"
alwaysApply: false
---

# Kafka / 消息队列规则

详见 [docs/architecture/messaging.md](../../docs/architecture/messaging.md)（待写时以本规则 + technology-stack 为准）。

## 铁律

1. **跨域异步必须走 Kafka** — 通知、索引、WS 扇出、转码、导出
2. **禁止**用 Redis List / Stream 充当业务事件总线
3. Topic / groupId **常量集中**在 `bluedock-common` 的 `KafkaTopics` / `ConsumerGroups`，禁止硬编码字符串
4. 消费者必须**幂等**（业务键去重或幂等表）
5. Worker 模块（`bluedock-worker-*`）**禁止暴露 HTTP**

## Topic 命名

点分小写，前缀 `bluedock.`，例如：

| Topic（示意） | 用途 |
| ------------- | ---- |
| `bluedock.notify.send` | 邮件 / 推送投递 |
| `bluedock.search.index` | 搜索增量索引 |
| `bluedock.realtime.fanout` | WS 扇出 |
| `bluedock.file.process` | 转码 / 打包 |

定稿以 `docs/architecture/messaging.md` 为准。

## 模式

- 集群：**KRaft**（Kafka **4.3.1**），无 ZooKeeper
- 本地：PLAINTEXT；生产：内网 PLAINTEXT 或 SASL_SSL
- 客户端：`spring-kafka`
- 可选 Outbox：业务事务内写 outbox → poller produce，保证至少一次投递

## 反模式

```java
// ❌ Redis 当 MQ
redis.opsForList().leftPush("bluedock.events", payload);

// ❌ 硬编码 topic
@KafkaListener(topics = "bluedock.notify.send")

// ✅
@KafkaListener(topics = KafkaTopics.NOTIFY_SEND, groupId = ConsumerGroups.NOTIFY)
```
