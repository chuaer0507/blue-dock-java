# BlueDock（蓝坞）— 文档索引

## 定位

**BlueDock（蓝坞）** 开源协作平台的 **Java 后端**设计与实现文档（本仓只覆盖后端接口与部署）。

| 维度 | 说明 |
| ---- | ---- |
| 后端栈 | **Java 25** · **Spring Boot 4.1.0** · **MyBatis-Plus 3.5.14** · **MySQL 9.7.2** · **Redis 8.2.8** Extended · **Kafka 4.3.1** · **Nginx 1.30.4**（详见 [technology-stack.md](architecture/technology-stack.md)） |
| 文档目标 | 按功能模块写清业务边界、API 契约、数据模型、权限与验收点 |
| 状态约定 | `[ ]` 待写 · `[~]` 起草中 · `[x]` 已定稿 |
| 优先级 | **产品 P0/P1**（下表）≠ **实现阶段**（见 [ops/migration.md](ops/migration.md)） |

> 本清单为「先列后写」入口。模块文档落在 `modules/<feature>/`，跨切面落在 `architecture/`、`contract/`、`data/`、`infra/`、`ops/`。

---

## 文档目录总览

| 目录 | 说明 |
| ---- | ---- |
| [architecture/](architecture/) | 总体架构、模块划分、技术栈、Kafka、实时通道 |
| [contract/](contract/) | API 契约、领域命名、路由约定 |
| [data/](data/) | 库表、ID、缓存、全文/搜索索引 |
| [modules/](modules/) | **按功能模块**的业务文档（主战场） |
| [infra/](infra/) | 上传、推送、会议、LDAP、License、AI 等基础设施 |
| [ops/](ops/) | 部署、迁移、回归清单 |

---

Agent 规则 / Skills / Commands 见仓库根 [`AGENTS.md`](../AGENTS.md) 与 [`.agents/`](../.agents/README.md)；Claude 手册见 [`CLAUDE.md`](../CLAUDE.md)。

---

## 0. 跨切面文档清单

### architecture/

| 文档 | 说明 | 状态 |
| ---- | ---- | ---- |
| [architecture.md](architecture/architecture.md) | 分层、模块边界、请求链路 | [x] |
| [services.md](architecture/services.md) | Java 服务 / 包划分与 API 归属 | [x] |
| [technology-stack.md](architecture/technology-stack.md) | 运行时 / DB / Redis / Kafka / Nginx | [x] |
| [messaging.md](architecture/messaging.md) | Kafka Topic、消费者组；Outbox 入队 + poller 投递 | [x] |
| [realtime.md](architecture/realtime.md) | WebSocket / 推送事件（可经 Kafka 扇出） | [x] |
| [search-index.md](architecture/search-index.md) | 全文检索：全量 / 增量同步方案 | [x] docs 默认 + OS 可选 |

### contract/

| 文档 | 说明 | 状态 |
| ---- | ---- | ---- |
| [api-contract.md](contract/api-contract.md) | REST 契约总表（按模块分章）+ 能力缺口 | [x] 主路径已齐；缺口见文末 parity |
| [api-routing.md](contract/api-routing.md) | 路由规则（`api/{resource}/{action}` 等） | [x] |
| [domain-naming.md](contract/domain-naming.md) | 领域命名（Project / Task / Dialog / File …） | [x] |
| [naming.md](contract/naming.md) | **禁止简写**词表（API / Java / 落库统一全词） | [x] |
| [i18n.md](contract/i18n.md) | 多语言与错误码约定（zh / en） | [x] |

### data/

| 文档 | 说明 | 状态 |
| ---- | ---- | ---- |
| [database.md](data/database.md) | 核心表与关系 | [x] 对齐 V1；Outbox 入队 + poller |
| [id-generation.md](data/id-generation.md) | ID 策略 | [x] |
| [redis.md](data/redis.md) | Key / TTL / 锁 / 会话 | [x] |

### infra/

| 文档 | 说明 | 状态 |
| ---- | ---- | ---- |
| [upload.md](infra/upload.md) | 分片上传（init / chunk / merge） | [x] |
| [oss-settings.md](infra/oss-settings.md) | 存储引擎（local/云，管理端可配） | [x] 已落地 |
| [admin-db-settings.md](infra/admin-db-settings.md) | 上传进库 + OSS/SMTP/AI Key 等管理配置落库 | [x] |
| [upload-objects.md](infra/upload-objects.md) | 上传库可管理（列表/上传/删除） | [x] 已落地 |
| [meeting-agora.md](infra/meeting-agora.md) | Agora 会议接入 | [x] setting/meeting 落库 + 密钥掩码 |
| [app-push.md](infra/app-push.md) | APP 推送 | [x] 别名 + 设置 + Worker + PC 延时队列 + `bluedock_app_push_logs` |
| [ldap.md](infra/ldap.md) | LDAP 按需同步（非定时全量） | [x] 登录认证 + 昵称同步 + ldapSyncLocal + 改密回写 |
| [license.md](infra/license.md) | License / 在线授权 | [x] 离线校验 + 在线 local/remote |
| [ai-assistant.md](infra/ai-assistant.md) | AI 助手与模型 Key（管理端可配，对齐 file/oss/email） | [x] 设置 API + Key 掩码；报告 AI；流式 SSE + embedding 匹配已落地 |
| [email.md](infra/email.md) | SMTP 邮件（管理端可配，对齐 file/oss） | [x] 设置 API + 密码掩码 + Worker SMTP + 未读汇总调度 |

### ops/

| 文档 | 说明 | 状态 |
| ---- | ---- | ---- |
| [deployment.md](ops/deployment.md) | 本地 / Compose / 生产部署 | [x] Compose + `.env` + K8s Kustomize |
| [migration.md](ops/migration.md) | 分阶段落地与数据迁移路线 | [x] P0–P5 基本完成；开放项见 api-contract 能力缺口 |
| [regression.md](ops/regression.md) | 按模块手工 / 自动化回归清单 | [x] 清单定稿；`[ ]` 为发版执行勾选 |

---

## 1. 功能模块文档清单

每个模块建议至少包含：

| 文件 | 用途 |
| ---- | ---- |
| `overview.md` | 业务边界、角色、与相邻模块关系 |
| `api.md` | 本模块 API 清单与关键路径 |
| `data.md` | 表 / 字段 / 状态机 |
| `permissions.md` | 权限点与身份（admin / owner / member …） |
| `checklist.md` | 实现与验收勾选 |

细项勾选见 [modules/CHECKLIST.md](modules/CHECKLIST.md)。

### B1 — 一级导航 + 项目/任务（P0）

| 模块 | 目录 | feature id | 核心 API | 状态 |
| ---- | ---- | ---------- | -------- | ---- |
| 仪表盘 | [modules/dashboard/](modules/dashboard/) | `dashboard` | `dashboard/team/*` + 任务聚合 | [x] |
| 日历 | [modules/calendar/](modules/calendar/) | `calendar` | 任务时间字段驱动 | [x] |
| 即时通讯 | [modules/messenger/](modules/messenger/) | `messenger` | `dialog/*` | [x] |
| 文件 | [modules/file/](modules/file/) | `file` | `file/*` | [x] |
| 项目 | [modules/project/](modules/project/) | `project` | `project/*`（列/成员/工作流/权限） | [x] |
| 任务 | [modules/task/](modules/task/) | `task` | `project/task/*`（模板/关联/AI） | [x] |

### B2 — 会议 / 报告 / 签到

| 模块 | 目录 | feature id | 优先级 | 状态 |
| ---- | ---- | ---------- | ------ | ---- |
| 会议 | [modules/meeting/](modules/meeting/) | `meeting` | P0 | [x] open/link/tourist/invitation + 关房 |
| 工作报告 | [modules/report/](modules/report/) | `report` | P0 | [x] |
| 签到打卡 | [modules/attendance/](modules/attendance/) | `attendance` | P1 | [x] |

### B3 — 应用中心 + 通知 + 机器人

| 模块 | 目录 | feature id | 优先级 | 状态 |
| ---- | ---- | ---------- | ------ | ---- |
| 应用中心（导航） | [modules/application/](modules/application/) | `application` | P0 | [x] |
| 系统应用 / 管理员应用 | [modules/apps/](modules/apps/) | `app-system` / `app-admin` | P0 | [x] |
| 微应用 | [modules/micro-app/](modules/micro-app/) | `micro-app` | P0 | [x] |
| 机器人 | [modules/bot/](modules/bot/) | `bot` | P0 | [x] CRUD + Webhook(message/成员/打开) |
| 通知（邮件/推送/桌面/移动） | [modules/notify/](modules/notify/) | `email-notice` / `push-notice` / `desktop-notify` / `mobile-notify` | P1 | [x] |
| AI 助手 | [modules/assistant/](modules/assistant/) | `assistant` | P0 | [x] |

### B4 — 用户组织 + 系统管理

| 模块 | 目录 | feature id | 优先级 | 状态 |
| ---- | ---- | ---------- | ------ | ---- |
| 账号 | [modules/user-account/](modules/user-account/) | `user-account` | P0 | [x] 主路径已落地 |
| 个人设置 | [modules/user-settings/](modules/user-settings/) | `user-settings` | P0 | [x] 主路径 + 个性标签 + privacy |
| 部门 | [modules/org-department/](modules/org-department/) | `org-department` | P0 | [x] API；部门群桥接已落地 |
| 角色与权限 | [modules/role-permission/](modules/role-permission/) | `role-permission` | P0 | [x] 四级权限 + 离职交接已落地 |
| 收藏与最近 | [modules/favorite/](modules/favorite/) | `favorite` | P0 | [x] 收藏 + 浏览/最近访问（含 task_file） |
| 系统设置 | [modules/system-setting/](modules/system-setting/) | `system-setting` | P1 | [x] |
| License | [modules/license/](modules/license/) | `license` | P1 | [x] 离线 + 在线 local/remote |
| LDAP | [modules/ldap/](modules/ldap/) | `ldap` | P1 | [x] |
| 数据导出 | [modules/data-export/](modules/data-export/) | `data-export` | P1 | [x] |
| 举报 | [modules/abuse-report/](modules/abuse-report/) | `abuse-report` | P2 | [x] |
| 合规 | [modules/compliance/](modules/compliance/) | `compliance` | P2 | [x] |
| 应用市场 | [modules/appstore/](modules/appstore/) | `appstore` | P0 | [x] 注册表闭环 |

### B5 / B6 — 横切

| 模块 | 目录 | feature id | 优先级 | 状态 |
| ---- | ---- | ---------- | ------ | ---- |
| 全局搜索 | [modules/search/](modules/search/) | `search` | P0 | [x] |
| AI 助手 | [modules/assistant/](modules/assistant/) | `assistant` | P0 | [x] |
| 分片上传 | [modules/upload/](modules/upload/) | `upload` | P0 | [x] |

> 快捷键、术语表、菜单入口索引、通用 FAQ 等横切内容，以 `ops/regression.md` 与各模块 `checklist.md` 覆盖，不单独拆业务模块。

---

## 2. 建议撰写顺序

| 阶段 | 内容 | 目的 |
| ---- | ---- | ---- |
| S0 | architecture + technology-stack + domain-naming | 定栈与边界 |
| S1 | user-account → role-permission → org-department | 身份底座 |
| S2 | project → task → dashboard | 核心任务域 |
| S3 | messenger → file → upload → search | 协作与内容 |
| S4 | report → meeting → calendar → attendance | 周边业务 |
| S5 | system-setting → apps → bot → notify → ldap → license | 管理与集成 |
| S6 | assistant + realtime + search-index + migration | 增强与落地 |

---

## 3. 进度统计

| 分区 | 文档数（约） | 已定稿 / 起草中 |
| ---- | ------------ | --------------- |
| 跨切面 architecture/contract/data/infra/ops | 21 | **已定稿 [x]**（冒烟勾选见 regression 执行栏） |
| 功能 modules（按模块 overview 计） | 32 | **多数 [x]**（见 CHECKLIST） |
| **合计** | **~53 入口文档** | 以 [modules/CHECKLIST.md](modules/CHECKLIST.md) 为准 |

模块内 `api.md` / `data.md` / `permissions.md` / `checklist.md`：主路径已齐；契约缺口见 [modules/CHECKLIST.md](modules/CHECKLIST.md)「契约未实现收口」（当前无待实现项）。

> **实现进度**以 [ops/migration.md](ops/migration.md) 为准；上表「P0」多为产品优先级。细则见 [contract/api-contract.md「能力缺口（parity）」](contract/api-contract.md#能力缺口parity)。

---

## 4. 未完成清单

REST `/api/*` 主路径与实现缺口 P0–P2 已齐。下列为仍开放项（本仓外 / 运维；变更时同步 api-contract 能力缺口 + [ops/migration.md](ops/migration.md)）。

### 实现 — P0

- [x] 自动归档调度
- [x] 待办到期推送
- [x] 机器人消息按 `clearDay` 清理

### 实现 — P1

- [x] 未领取任务提醒（`unclaimedTaskReminder` / `unclaimedTaskReminderTime` + 调度）
- [x] 任务 AI 自动扫描（`TaskAiScanScheduler`）

### 实现 — P2

- [x] AI 会话标题自动生成
- [x] `/avatar` 字母头像
- [x] `/drawio/iconsearch`
- [x] `/online/preview`

### 本仓外 / 运维

- [ ] face / approve 插件（本仓仅桥接）
- [ ] 发版回归冒烟勾选（见 [ops/regression.md](ops/regression.md)）
