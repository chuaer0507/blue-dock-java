# Agents

本文件供 **Cursor**、**Codex**、以及经 `CLAUDE.md` 的 `@AGENTS.md` 导入的 **Claude Code** 在开工前注入。  
**规则正文唯一源**：[`.agents/`](.agents/README.md)。Claude 兼容层：`.claude/`（settings + symlink）。

## 开工前必做

Cursor / Codex **不会**自动注入 `.agents/rules/*.md` 全文。  
**改代码前必须按任务 `Read` 对应规则文件**（路径均在 `.agents/rules/`）：

| 任务类型 | 先读 |
|----------|------|
| 任意改动 | `architecture.md`、`behavior.md`、`doc-sync.md` |
| Controller / API | `api-contract.md`、`json-naming.md`、`password-wire.md`、`i18n.md`；命名词表 `docs/contract/naming.md` |
| 错误文案 / 多语言 | `i18n.md` |
| Kafka / Worker | `messaging.md` |
| DB / Mapper / Flyway | `database.md` |
| Redis | `redis.md` |
| 新建 / 拆模块 | `modules.md` |

Skills：`.agents/skills/<name>/SKILL.md`  
（`check` / `code-reviewer` / `create-module` / `create-controller` / `create-service` / `create-migration` / `create-worker`）

Commands：`.agents/commands/`（经 `.claude/commands` symlink）  
（`/check` / `/review` / `/create-module` / `/create-controller` / `/create-service` / `/create-migration` / `/create-worker`）

## 沟通

- 所有沟通、注释、文档、规则、skill、回复使用**中文**

## 架构铁律（违反即 Bug）

1. **API 契约向前兼容** — REST `/api/...`、WS `/ws`；JSON camelCase；改契约须同步 `docs/contract/api-contract.md`
2. **按领域组织模块** — `bluedock-{domain}`；禁止按客户端形态（desktop/web）拆后端包
3. **强一致写 MySQL，热路径读 Redis** — 项目/任务/消息/文件元数据落库；会话、在线状态、限流、锁走 Redis
4. **跨域异步走 Kafka** — 通知投递、索引同步、WS 扇出、转码/导出走 Kafka；**禁止**用 Redis List/Stream 当业务事件总线
5. **权限先于数据** — 读写下沉到 Service，按项目/任务/部门角色校验；禁止仅靠前端藏按钮

依赖：`bluedock-boot` → `bluedock-{domain}` → `bluedock-common`；Worker 同理，禁止暴露 HTTP。

## 强制要点（摘要）

- **JSON wire**：camelCase **全词**，**禁止简写**（词表 `docs/contract/naming.md`）；`password` 不得出现在读响应
- **多语言**：API `message` 支持 zh-CN/en-US；抛错用 `I18nKeys` + `i18n/messages_zh_CN|en_US.properties`，禁止硬编码文案（见 `.agents/rules/i18n.md`）
- **Redis Key**：常量集中在 `bluedock-common` `RedisKeys`，禁止硬编码字符串
- **Kafka Topic**：常量集中在 `bluedock-common` `KafkaTopics` / `ConsumerGroups`
- **路径唯一**：每个 REST 接口只有一个 prefix，禁止别名
- **改完**：同步 `docs/`；手册变更同步 `CLAUDE.md` / `README.md`；铁律摘要变更改本文件

完整条文以 `.agents/rules/` 为准。
