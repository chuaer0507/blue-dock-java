# Kafka / 消息队列

跨域异步副作用总线。铁律见 [`.agents/rules/messaging.md`](../../.agents/rules/messaging.md)。

## 原则

1. 业务强一致写落 **MySQL**；Kafka 只承载**事务提交之后**的副作用
2. **禁止** Redis List / Stream 当业务 MQ
3. Topic / groupId 常量集中在 `bluedock-common`（`KafkaTopics` / `ConsumerGroups`）
4. 消费者必须幂等
5. Worker（`bluedock-worker-*`）不暴露 HTTP

## 集群

| 项 | 约定 |
| -- | ---- |
| 版本 | Kafka **4.3.1**，**KRaft**（无 ZooKeeper） |
| 客户端 | `spring-kafka` |
| 本地 | PLAINTEXT |
| 生产 | 内网 PLAINTEXT 或 SASL_SSL |

## Topic（首版）

| Topic | 生产者（示意） | 消费者 | 用途 |
| ----- | -------------- | ------ | ---- |
| `bluedock.notify.send` | `NotifySendPublisher`（bluedock-system）；会话消息 `DialogAppPushNotifyService`；未读汇总 `UnreadEmailNoticeService` | `bluedock-worker-notify`：`EmailNotifyChannel` / `AppPushChannel`（免打扰过滤 + PC 延时 ZSET + `bluedock_app_push_logs`；幂等 idempotency） | 邮件 SMTP、APP 推送；desktop 由客户端本地处理 |
| `bluedock.search.index` | `SearchIndexPublisher`（bluedock-search；messenger 等已接入） | `bluedock-worker-index` → `bluedock_search_docs` | 搜索增量索引 / 删除 |
| `bluedock.realtime.fanout` | 领域写路径（messenger / task / apps / assistant）经 `RealtimeFanoutPublisher` | 每个 `bluedock-boot` 实例（独立 groupId `bluedock-realtime-{uuid}`） | WS 多节点广播扇出 |
| `bluedock.file.process` | file/upload | worker | 转码、打包、缩略图 |
| `bluedock.export.run` | bluedock-task / bluedock-system（导出入口） | `bluedock-worker-notify`（`ConsumerGroups.EXPORT`）→ CSV + Redis 下载票 + `bluedock.notify.send` + `bluedock.export.notify` | 任务统计 / 超期 / 签到 / 审批导出 |
| `bluedock.export.notify` | `bluedock-worker-notify`（导出完成/失败） | boot/`bluedock-boot-export-notify` → `ExportNotifyBridge`（`system-msg` 私聊） | 导出结果私聊 |
| `bluedock.userBot.webhook` | messenger（发消息后） | `bluedock-worker-notify` | 用户机器人 Webhook HTTP POST |
| `bluedock.userBot.webhook.reply` | notify worker（Webhook 返回 200+message） | boot/`bluedock-boot-userBot-webhook-reply` | 机器人文本回复入库 + WS 扇出 |

命名：点分小写，前缀 `bluedock.`。定稿后改常量与本文同步。

## Outbox（已启用）

`bluedock_outbox`（V1，含 `message_key`）+ boot 内 poller。

```
业务事务 → 写业务表 + outbox 行（OutboxWriter）→ 提交
       → OutboxPoller（FOR UPDATE SKIP LOCKED）→ KafkaTemplate.send → published_at
```

| 组件 | 位置 |
| ---- | ---- |
| `OutboxWriter` | `bluedock-common` 契约 |
| `JdbcOutboxWriter` / `OutboxDispatchService` / `OutboxPoller` | `bluedock-boot` |
| 发布器 | `KafkaRealtimeFanoutPublisher` 等：有活跃事务则入队，否则直发 |

配置：`bluedock.outbox.enabled`（默认 true）· `poll-ms` · `batch-size`。

至少一次投递；重复消息靠消费者幂等消化。关闭 outbox 时发布器退回直发 Kafka。

## 与实时通道

- 单机可：写库后同进程推 WS
- 多实例：**先**发 `bluedock.realtime.fanout`，再由各节点推本地连接

见 [realtime.md](realtime.md)。
