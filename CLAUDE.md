# CLAUDE.md

@AGENTS.md

Claude Code 专用入口：通过上方 `@AGENTS.md` 继承跨工具约束与铁律摘要。  
规则正文在 `.agents/rules/`；skills 在 `.agents/skills/`。`.claude/` 为 Claude 兼容层。

## 文档同步（Claude）

- 改完代码后、回复前：同步更新 `docs/`（见 `.agents/rules/doc-sync.md`）
- 若本手册（命令 / 架构 / CI）有变：同步 `CLAUDE.md` 与 `README.md`
- 若 agent 铁律摘要有变：改 `AGENTS.md`（勿在本文件重复抄一遍强制规则）

## 项目概览

**BlueDock（蓝坞）** 开源协作平台 **后端**：可部署的 Java 服务栈。API 契约以 `docs/contract/api-contract.md` 为唯一来源。

**技术栈**：Java 25 · Spring Boot 4.1.0 · MyBatis-Plus 3.5.17 · MySQL 9.7.2 · Redis 8.2.8 Extended · Kafka 4.3.1 · Nginx 1.30.4

**当前阶段**：P0–P5 业务 API **基本完成**（见 [ops/migration.md](docs/ops/migration.md)）；能力缺口见 [contract/api-contract.md](docs/contract/api-contract.md#能力缺口parity)。Compose 见 `deploy/`。发版冒烟见 [ops/regression.md](docs/ops/regression.md)。入口见 [docs/README.md](docs/README.md)、[technology-stack.md](docs/architecture/technology-stack.md)。

---

## 命令

```bash
make compile && make test && make package
make dev-up && make run-boot   # 或 make dev；对外 Nginx :18080
```

表结构变更走 Flyway：`bluedock-boot/src/main/resources/db/migration/V{version}__{description}.sql`。  
版本判定见 `.agents/rules/database.md`。

---

## 目录结构

```
BlueDock/
├── bluedock-common/           # ResultModel、异常码、Redis / Kafka 常量、IdGenerator
├── bluedock-auth/             # 认证（JWT、RSA 密码 wire、验证码）
├── bluedock-user/             # 用户资料、设置、收藏
├── bluedock-org/              # 部门、角色权限
├── bluedock-project/          # 项目、列、工作流
├── bluedock-task/             # 任务、模板、标签
├── bluedock-messenger/        # 会话、消息
├── bluedock-file/             # 文件、上传元数据
├── bluedock-report/           # 工作报告
├── bluedock-system/           # 系统设置、License、LDAP
├── bluedock-search/           # 搜索
├── bluedock-assistant/        # AI 助手
├── bluedock-realtime/         # WebSocket
├── bluedock-worker-notify/    # 通知 Worker（无 HTTP）
├── bluedock-worker-index/     # 索引 Worker（无 HTTP）
├── bluedock-boot/             # 可执行 JAR；db/migration、db/seed
├── docs/                  # 设计文档
├── deploy/                # Compose、脚本、Nginx
├── .agents/               # Agent 规则 / skills / commands（唯一内容源）
├── AGENTS.md              # Cursor / Codex 启动指令
└── .claude/               # Claude Code 兼容层（symlink → .agents）
```

包命名：`com.bluedock.{module}.{layer}`（如 `com.bluedock.project.service`）。

---

## 架构铁律

违反即为 Bug。细则见 `.agents/rules/architecture.md`；摘要见 `AGENTS.md`。

1. **API 向前兼容** — `/api/...`、`/ws`；契约以 `docs/contract/api-contract.md` 为唯一来源
2. **按领域组织，不按客户端形态** — 禁止按 desktop/web 拆后端包
3. **强一致写 MySQL，热路径读 Redis**
4. **跨域异步走 Kafka** — 禁止 Redis 当业务 MQ
5. **权限先于数据** — Service 内校验项目/任务/部门角色

### 分层

| 层 | 职责 | 禁止 |
| -- | ---- | ---- |
| Controller | 路由、校验、`ResultModel` | 业务逻辑 |
| Service | 领域逻辑、事务、权限 | 跨模块直接调 Mapper |
| Mapper | MyBatis-Plus SQL | — |
| Worker | Kafka 消费、异步副作用 | 暴露 HTTP |

### 依赖方向

```
bluedock-boot → bluedock-{domain} → bluedock-common
bluedock-worker-* → bluedock-{domain} → bluedock-common
```

---

## 关键约定

- **时间**：UTC 存 `DATETIME(3)`
- **JSON wire**：camelCase（见 `.agents/rules/json-naming.md`）
- **多语言**：API `message` 支持 zh/en；抛错用 `I18nKeys`（见 `.agents/rules/i18n.md`）
- **密码**：请求 RSA + `keyId`；响应禁止回传（见 `password-wire.md`）
- **Redis Key**：`bluedock:...`，常量在 `RedisKeys`
- **Kafka Topic**：`bluedock.*`，常量在 `KafkaTopics`
- **路径唯一**：每个 REST 接口一个 prefix，禁止别名
- **响应**：统一 `ResultModel<T>`

---

## 文档索引

以 `docs/README.md` 为入口：

| 目录 | 内容 |
| ---- | ---- |
| `docs/architecture/` | 架构、services、技术栈、messaging、realtime |
| `docs/contract/` | api-contract、api-routing、domain-naming、i18n |
| `docs/data/` | database、redis、id-generation |
| `docs/modules/` | 按功能模块业务文档 |
| `docs/infra/` | 上传、会议、推送、LDAP、License、AI、邮件 |
| `docs/ops/` | deployment、migration、regression |

**文档同步**：每次代码变更后主动更新受影响的 `docs/`。触发表见 `.agents/rules/doc-sync.md`。

---

## CI 工作流

完整说明见 [`.github/WORKFLOWS.md`](.github/WORKFLOWS.md)。

| Workflow | 触发 | 说明 |
| --- | ---- | ---- |
| `ci-pull-requests` | PR → `main` | check → k8s / compose-smoke（`--env-file .env.dev`） |
| `ci-main` | push → `main` / Tag / 手动 | 同上；Tag 或手动 + `release-images` → GHCR |
| `ci-check-pr-title` | PR 标题 | Angular 标题校验 |

本地 commit：`.githooks/commit-msg`（`bash scripts/setup_githooks.sh` 或 `make githooks`）。

Composite：`setup-java`（JDK `25`，与技术栈同步）。镜像 tag 与 `deploy/.env.*` 对齐见 `make sync-env-tag`。

---

## AI 规则

内容源见 [`.agents/README.md`](.agents/README.md) 与根目录 [`AGENTS.md`](AGENTS.md)。勿在 `.claude/` 编辑规则正文。

| 路径 | 说明 |
|------|------|
| [`.agents/`](.agents/README.md) | 唯一内容源：`rules/` / `skills/` / `commands/` |
| [`AGENTS.md`](AGENTS.md) | Cursor / Codex 启动指令（本文件经 `@AGENTS.md` 继承） |
| [`.claude/`](.claude/README.md) | Claude 兼容层（settings + symlink） |

---

## 禁止事项

- 改 API 路径 / JSON 形态而不更新 `docs/contract/api-contract.md`
- 用 Redis List/Stream 做业务事件队列（应走 Kafka）
- 在业务代码硬编码 Redis Key / Kafka Topic 字符串
- 硬编码中英文错误文案（须用 `I18nKeys` + `i18n/messages_zh_CN|en_US.properties`）
- Worker 暴露 HTTP
- 读响应回传 `password` / `passwordHash`
- 按 desktop/web 拆后端 Maven 模块
