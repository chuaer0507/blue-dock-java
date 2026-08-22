# 总体架构

## 形态

**模块化单体 + 独立 Worker**：

```
客户端
    → Nginx
        → bluedock-boot（REST + WebSocket + Outbox）
            → MySQL 9.7.2（强一致）
            → Redis 8.2.8（会话 / 缓存 / 锁）
            → Kafka 4.3.1（异步副作用）
                → bluedock-worker-notify / bluedock-worker-index / …
```

铁律见 [`AGENTS.md`](../../AGENTS.md) / [`.agents/rules/architecture.md`](../../.agents/rules/architecture.md)。本仓只维护后端；客户端工程不在此仓。

## 分层

| 层 | 职责 |
| -- | ---- |
| Controller | 路由、校验、`ResultModel` |
| Service | 领域逻辑、事务、权限 |
| Mapper | SQL |
| Worker | Kafka 消费，无 HTTP |

## 领域模块（目标）

见 [services.md](services.md) 与 [`.agents/rules/modules.md`](../../.agents/rules/modules.md)：

`bluedock-auth` · `bluedock-user` · `bluedock-org` · `bluedock-project` · `bluedock-task` · `bluedock-messenger` · `bluedock-file` · `bluedock-report` · `bluedock-system` · `bluedock-search` · `bluedock-assistant` · `bluedock-realtime` · Workers · `bluedock-boot`

## 功能域与文档

业务能力说明按 [modules/](../modules/) 分册；本文件只定**工程边界**，不写产品操作步骤。

## 实时与异步

| 通道 | 用途 |
| ---- | ---- |
| WebSocket | 即时同步（消息、任务字段、在线状态） |
| Kafka | 通知投递、搜索索引、扇出、转码/导出 |

详见 [realtime.md](realtime.md)、[messaging.md](messaging.md)。
