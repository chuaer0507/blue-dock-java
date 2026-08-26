# Agent 配置（唯一内容源）

跨工具共用目录，遵循 [Agent Skills](https://agentskills.io) / `.agents` 约定。  
适用：**Cursor**、**Claude Code**、**Codex**，以及其他会扫描 `.agents/` / `AGENTS.md` 的 agent 运行时。

请只在 `.agents/` 与根目录 `AGENTS.md` / `CLAUDE.md` 编辑。

## 目录

| 路径 | 说明 |
|------|------|
| `rules/*.md` | 项目规则（YAML frontmatter：`description` / `alwaysApply` / `globs`） |
| `skills/<name>/SKILL.md` | Agent Skills（`name` + `description`） |
| `commands/` | 自定义 commands（Claude 经 symlink；亦可直接读或用 skill） |

## 各工具如何发现

| 工具 | 启动指令 | rules | skills |
|------|----------|-------|--------|
| Cursor | 根目录 `AGENTS.md` | **须主动 Read** `.agents/rules/` | `.agents/skills/` |
| Claude Code | `CLAUDE.md` → `@AGENTS.md` | `.claude/rules` → symlink；`settings.json` 可直指 `.agents/rules` | `.agents/skills` / `.claude/skills` |
| Codex | 根目录 `AGENTS.md` | **须主动 Read** `.agents/rules/` | `.agents/skills` |
| 其他 | 优先 `AGENTS.md` | 按 `AGENTS.md` 表格读取 | `.agents/skills/<name>/SKILL.md` |

## 工具专属

| 文件 | 工具 |
|------|------|
| 根目录 `AGENTS.md` | Cursor / Codex 等跨工具启动指令（铁律摘要） |
| 根目录 `CLAUDE.md` | Claude Code：`@AGENTS.md` + 项目手册 |
| `.claude/settings.json` | Claude Code 权限 / hooks / rules 入口 |
| `.claude/settings.local.json` | 本机 Claude 权限（gitignore） |

## 编辑约定

1. 改规则 / skill / command → 只改 `.agents/`
2. 不要维护双份拷贝
3. 新 skill 必须是 `skills/<name>/SKILL.md`

## 现有 rules

| 规则 | 作用域 | 说明 |
|------|--------|------|
| `architecture.md` | 始终 | 五条铁律、分层、依赖方向 |
| `behavior.md` | 始终 | Karpathy 行为准则（HOW） |
| `doc-sync.md` | 始终 | 代码变更后同步 `docs/` |
| `json-naming.md` | 始终 | JSON wire camelCase |
| `api-contract.md` | Controller | REST 契约、`ResultModel`、路径唯一 |
| `i18n.md` | Service / Controller / properties | 多语言 zh/en、`I18nKeys`、禁止硬编码文案 |
| `password-wire.md` | Service / Controller / DTO | RSA 传密、禁止响应回传 |
| `messaging.md` | Kafka / Worker | Topic、幂等、禁止 Redis 当 MQ |
| `database.md` | DB / Mapper / SQL | 命名、Flyway |
| `redis.md` | Redis | Key 规范、TTL |
| `modules.md` | Maven 子模块 | 模块边界与包结构 |

## 现有 skills

| Skill | 用途 |
|-------|------|
| `check` | `mvn compile` / `test` / 架构与契约抽查（`/check`） |
| `code-reviewer` | 按规则审查代码变更（`/review`） |
| `create-module` | 新建 `bluedock-<name>` 领域子模块（`/create-module`） |
| `create-controller` | 新建 REST Controller（`/create-controller`） |
| `create-service` | 新建 Service + Mapper + Entity（`/create-service`） |
| `create-migration` | 创建 / 修改 Flyway 迁移（`/create-migration`） |
| `create-worker` | 新建 Kafka Worker 消费者（`/create-worker`） |

## 现有 commands

| 命令 | 说明 |
|------|------|
| `/check` | → skill `check` |
| `/review` | → skill `code-reviewer` |
| `/create-module` | → skill `create-module` |
| `/create-controller` | → skill `create-controller` |
| `/create-service` | → skill `create-service` |
| `/create-migration` | → skill `create-migration` |
| `/create-worker` | → skill `create-worker` |
