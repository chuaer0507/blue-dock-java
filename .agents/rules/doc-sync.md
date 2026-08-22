---
description: 文档同步 — 代码变更后主动更新 docs/
alwaysApply: true
---

# 文档同步规则

**每次完成代码修改后、回复用户之前，必须主动同步更新 `docs/` 下受影响的文档。不可跳过。**

## 触发场景

| 变更类型 | 更新文档 |
| -------- | -------- |
| 新增/修改 API 路由或响应 | `docs/contract/api-contract.md`（**一接口一路径**，禁止别名）；命名见 `docs/contract/domain-naming.md` |
| 领域命名变更 | `docs/contract/domain-naming.md`、`docs/contract/naming.md`（与前端 `packages/shared` 同步） |
| 字段简写扩写 | `docs/contract/naming.md` 词表 + `api-contract.md` + 相关 module api + `database.md` |
| 多语言 / 错误文案 | `docs/contract/i18n.md`；规则见 [i18n.md](i18n.md) |
| 调整模块边界或 Worker | `docs/architecture/services.md`、`docs/architecture/architecture.md` |
| 新增/修改表结构 | `docs/data/database.md` + Flyway 约定（见 [database.md](database.md)） |
| 新增/修改 Redis Key | `docs/data/redis.md` |
| 密码传输 / 写接口 `password`+`keyId` | `docs/contract/api-contract.md`；`.agents/rules/password-wire.md` |
| Kafka Topic / 消费者组变更 | `docs/architecture/messaging.md` |
| 实时 / WS 事件变更 | `docs/architecture/realtime.md` |
| 部署配置变更 | `docs/ops/deployment.md` |
| 功能模块业务变更 | `docs/modules/<feature>/` |
| 部署 / Compose / 镜像 | `docs/ops/deployment.md` |
| JSON 字段命名 / camelCase | `docs/contract/api-contract.md`、`.agents/rules/json-naming.md` |
| 技术栈版本升级 | `docs/architecture/technology-stack.md` |
| Agent 规则 / skill / command / 铁律摘要 | `.agents/`（唯一正文）；摘要改 `AGENTS.md`；Claude 手册改 `CLAUDE.md`；勿改 `.claude/` 拷贝 |

## 流程

1. **先读文档，后写代码** — 实现前确认 `docs/` 已有设计
2. **改代码，同步文档** — 不等用户提醒
3. **提交前检查** — 相关文档与 Flyway SQL 已更新

## docs/ 索引

以 [docs/README.md](../../docs/README.md) 为入口：`architecture/` · `contract/` · `data/` · `modules/` · `infra/` · `ops/`。

Agent 配置见仓库根 [AGENTS.md](../../AGENTS.md) 与 [`.agents/README.md`](../../.agents/README.md)。
