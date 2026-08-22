# BlueDock（蓝坞）

**BlueDock（蓝坞）** 开源协作平台 **后端**——可部署、可扩展的 Java 服务栈（REST `/api/...`、WS `/ws`）。提供项目管理、任务分发、文档协作、思维导图、流程图、加密 IM、文件管理等能力。

**当前阶段**：P0–P5 业务 API **基本完成**（进度见 [docs/ops/migration.md](docs/ops/migration.md)）；能力缺口见 [docs/contract/api-contract.md](docs/contract/api-contract.md#能力缺口parity)（REST 主路径基本齐，定时/页面端点有缺口）。Compose 本地全栈可跑。发版冒烟见 [docs/ops/regression.md](docs/ops/regression.md)。文档入口：[docs/README.md](docs/README.md)。

---

## 技术栈

| 组件 | 版本 | 职责 |
| ---- | ---- | ---- |
| Java | 25（LTS） | 应用运行时 |
| Spring Boot | 4.1.0 | Web、Security、Scheduling |
| MyBatis-Plus | 3.5.14 | ORM |
| MySQL | 9.7.2 | 强一致业务数据 |
| Redis | 8.2.8 Extended | 缓存、会话、限流、分布式锁 |
| Kafka | 4.3.1（KRaft） | 通知、索引、WS 扇出、转码/导出 |
| Nginx | 1.30.4 | 反向代理、WebSocket 升级、限流 |

版本钉选详见 [docs/architecture/technology-stack.md](docs/architecture/technology-stack.md)。

---

## 架构概览

```
客户端
    │  HTTP / WS
    ▼
┌─────────────────────────────────────┐
│  Nginx 1.30.4（:18080）              │
│  · /api/*  → 127.0.0.1:8080          │
│  · /ws     → 127.0.0.1:8080          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  bluedock-boot（REST + WS + Outbox）     │
│  · 异步副作用由 Worker 消费          │
└──────────┬──────────────────────────┘
           │ produce
           ▼
      Kafka 4.3.1（KRaft）
           │ consume
     ┌─────┴──────┐
     ▼            ▼
┌──────────┐  ┌──────────┐
│ worker-  │  │ worker-  │
│ notify   │  │ index    │
└────┬─────┘  └────┬─────┘
     └──────┬──────┘
            ▼
     MySQL 9.7.2  Redis 8.2.8
```

设计原则：**模块化单体 + 独立 Worker**；按**领域**组织代码；强一致写 MySQL，热路径读 Redis，跨域异步走 Kafka。

---

## 快速开始

### 前置条件

- JDK 25（Temurin / Homebrew OpenJDK 25）
- Maven 3.9+
- Docker（本地 MySQL / Redis / Kafka / Nginx，见 `docs/ops/deployment.md`）

### 构建

```bash
make help
make githooks
make fmt
make compile && make test && make package
make sync-env-tag      # 按 git tag 写入 deploy/.env.* 的 BLUEDOCK_VERSION（镜像同 tag）
make dev-up            # MySQL / Redis / Kafka / Nginx（宿主机端口见 deployment.md）
make run-boot          # 前台；或 make dev 后台（boot :8080 仅 Nginx 回源，不对外）
# 对外入口（Nginx）：
# GET http://127.0.0.1:18080/api/system/version
# GET http://127.0.0.1:18080/api/users/key/client
# GET http://127.0.0.1:18080/api/users/login?email=<见 deploy/.env.dev #admin账号>&password=<RSA密文>&keyId=...
```

本地依赖与端口见 [docs/ops/deployment.md](docs/ops/deployment.md)（API 经 Nginx `:18080`）。首次启动无超管时 bootstrap 写入 `deploy/.env.dev` 的 `#admin账号：` / `#admin密码：`（邮箱即登录用户名）。

文档：[docs/README.md](docs/README.md) · [AGENTS.md](AGENTS.md)

---

## 目录结构

```
blue-dock-java/
├── pom.xml
├── bluedock-common/           # ResultModel、异常码、Redis / Kafka 常量、IdGenerator
├── bluedock-auth/             # 认证
├── bluedock-user/             # 用户
├── bluedock-org/              # 部门 / 权限
├── bluedock-project/          # 项目
├── bluedock-task/             # 任务
├── bluedock-messenger/        # IM
├── bluedock-file/             # 文件
├── bluedock-report/           # 工作报告
├── bluedock-system/           # 系统设置
├── bluedock-search/           # 搜索
├── bluedock-assistant/        # AI 助手
├── bluedock-realtime/         # WebSocket
├── bluedock-worker-notify/    # 通知 Worker
├── bluedock-worker-index/     # 索引 Worker
├── bluedock-boot/             # 可执行装配 + Flyway
├── docs/
├── deploy/
├── .agents/
├── AGENTS.md
└── CLAUDE.md
```

> 多模块结构已落地；领域代码在各 `com.bluedock.*` 包。剩余缺口见 [docs/modules/CHECKLIST.md](docs/modules/CHECKLIST.md) 与 [migration.md](docs/ops/migration.md)（人脸插件、发版回归）。

包命名：`com.bluedock.{module}.{layer}`（如 `com.bluedock.project.service`）。

---

## 架构铁律

违反即为 Bug。摘要见 [`AGENTS.md`](AGENTS.md)；细则见 [`.agents/rules/architecture.md`](.agents/rules/architecture.md)。

1. **API 向前兼容** — 契约唯一来源 `docs/contract/api-contract.md`
2. **按领域组织，不按客户端形态拆包**
3. **强一致写 MySQL，热路径读 Redis**
4. **跨域异步走 Kafka**（禁止 Redis 当业务 MQ）
5. **权限先于数据**

---

## 文档索引

| 目录 | 说明 |
| ---- | ---- |
| [docs/README.md](docs/README.md) | 文档总入口与模块清单 |
| [docs/architecture/technology-stack.md](docs/architecture/technology-stack.md) | 技术栈钉选 |
| [docs/contract/api-contract.md](docs/contract/api-contract.md) | REST 契约总表与能力缺口 |
| [docs/modules/CHECKLIST.md](docs/modules/CHECKLIST.md) | 功能模块写作细项 |
| [`.agents/README.md`](.agents/README.md) | Agent 规则 / skills / commands |

改代码后必须同步 `docs/`（见 [`.agents/rules/doc-sync.md`](.agents/rules/doc-sync.md)）。

---

## AI / Agent

| 路径 | 说明 |
|------|------|
| [`AGENTS.md`](AGENTS.md) | Cursor / Codex 启动指令 |
| [`CLAUDE.md`](CLAUDE.md) | Claude Code：`@AGENTS.md` + 手册 |
| [`.agents/`](.agents/README.md) | 规则 / skills / commands 唯一正文 |
| [`.claude/`](.claude/README.md) | Claude 兼容层（settings + symlink） |

---

## 许可证

本项目采用 [GNU Affero General Public License v3.0](LICENSE)（AGPL-3.0）。全文见根目录 [`LICENSE`](LICENSE)。
