---
description: 架构铁律 — 模块化单体、分层、设计原则
alwaysApply: true
---

# 架构铁律

## 五条铁律（违反即为 Bug）

1. **API 契约向前兼容** — 路径保持 `/api/...`、WS `/ws`；JSON camelCase；改契约须同步 [docs/contract/api-contract.md](../../docs/contract/api-contract.md)
2. **按领域组织代码，不按客户端形态组织** — 业务在 `bluedock-project`、`bluedock-messenger` 等领域模块；禁止按 `desktop`/`web` 拆后端包
3. **强一致写 MySQL，热路径读 Redis** — 项目、任务、会话消息、文件元数据落库；Token、在线状态、限流、分布式锁走 Redis
4. **跨域异步走 Kafka** — 通知、搜索索引、WS 扇出、转码/导出；**禁止**用 Redis List/Stream 当业务 MQ；详见 [messaging.md](messaging.md)
5. **权限先于数据** — 项目/任务/部门角色在 Service 校验；禁止仅靠前端藏入口

## 分层职责

| 层 | 职责 | 禁止 |
| -- | ---- | ---- |
| Controller | 路由、参数校验、`ResultModel` 包装 | 业务逻辑、直接操作 Redis 锁 |
| Service | 领域逻辑、事务边界、权限校验 | 依赖具体 HTTP 框架类型 |
| Mapper | MyBatis-Plus SQL | 跨模块直接调用 Mapper |
| Worker | Kafka 消费、异步副作用 | 对外暴露 HTTP（`bluedock-worker-*`） |

## 依赖方向

```
bluedock-boot → bluedock-{domain} → bluedock-common
bluedock-worker-* → bluedock-{domain} → bluedock-common
```

`bluedock-common` 零领域依赖；领域模块之间通过 Service 接口或 Kafka 事件通信，禁止循环依赖。

## 技术栈（固定）

Java **25** · Spring Boot **4.1.0** · MyBatis-Plus **3.5.17** · MySQL **9.7.2** · Redis **8.2.8** Extended · Kafka **4.3.1** · Nginx **1.30.4**

本仓只维护后端；客户端工程不在此仓。详见 [docs/architecture/technology-stack.md](../../docs/architecture/technology-stack.md)。
