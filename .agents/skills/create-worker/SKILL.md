---
name: create-worker
description: 创建 Kafka Worker 消费者（通知、索引等副作用）
---

# 创建 Kafka Worker

Worker 模块（`bluedock-worker-*`）**无 HTTP 端点**，仅消费 Kafka 事件。

实现前阅读 `docs/architecture/messaging.md` 与 [messaging.md](../../rules/messaging.md)。

## 检查清单

- [ ] Worker 模块无 `controller/` 目录？
- [ ] Topic / groupId 使用 `KafkaTopics` / `ConsumerGroups` 常量？
- [ ] 消费者幂等？
- [ ] 消费失败不会确认消息；重试与死信策略符合现有消费端配置？
- [ ] 消费处理涉及敏感数据或关键状态时，权限边界、审计记录与异常日志不泄漏敏感信息？
- [ ] 未用 Redis List 顶替 Kafka？
- [ ] 已更新 `docs/architecture/messaging.md`？

## 文件位置

```
bluedock-worker-<name>/src/main/java/com/bluedock/worker/<name>/
├── consumer/<Topic>Consumer.java
├── handler/<Event>Handler.java
└── config/KafkaConsumerConfig.java
```

## Consumer 模板

```java
package com.bluedock.worker.notify.consumer;

import com.bluedock.common.kafka.ConsumerGroups;
import com.bluedock.common.kafka.KafkaTopics;
import com.bluedock.worker.notify.handler.NotifySendHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifySendConsumer {

  private final NotifySendHandler handler;

  @KafkaListener(
      topics = KafkaTopics.NOTIFY_SEND,
      groupId = ConsumerGroups.NOTIFY,
      containerFactory = "kafkaManualAckContainerFactory")
  public void onMessage(String payload, Acknowledgment ack) {
    handler.handle(payload);
    ack.acknowledge();
  }
}
```

## 幂等

业务键去重（如 `eventId` / `userId+template+day`）；失败可重试，须可安全重复执行。
