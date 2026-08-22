---
description: 创建 Kafka Worker 消费者
argument-hint: <notify|index|...> <ConsumerName>
---

调用 [create-worker](../skills/create-worker/SKILL.md) 技能，在 `bluedock-worker-$1/` 下创建消费者。

- `$1`：Worker 短名（如 `notify`、`index`）
- `$2`：消费者类名（如 `NotifySendConsumer`）

Worker **无 HTTP**。完成后按 [doc-sync.md](../rules/doc-sync.md) 更新 `docs/architecture/messaging.md`。
