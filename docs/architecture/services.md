# Java 服务 / 模块划分

目标 Maven 多模块与 API 归属。包名：`com.bluedock.{module}.{layer}`。

## 模块清单

| 模块 | 职责 | 主要 API 前缀 |
| ---- | ---- | ------------- |
| `bluedock-common` | ResultModel、错误码、Redis/Kafka 常量、Id | — |
| `bluedock-auth` | 登录、注册、验证码、扫码、Token、设备 | `api/users/login*` 等 |
| `bluedock-user` | 资料、设置、收藏、最近、标签 | `api/users/*`（非组织） |
| `bluedock-org` | 部门、系统管理员授予 | `api/users/department*`、`operation` |
| `bluedock-project` | 项目、列、成员、工作流、权限、标签 | `api/project/*`（非 task 深度） |
| `bluedock-task` | 任务、子任务、模板、关联、AI 建议 | `api/project/task*` |
| `bluedock-messenger` | 会话、消息、群管理 | `api/dialog/*` |
| `bluedock-file` | 文件树、共享、版本、分片元数据 | `api/file/*`、`api/upload/*` |
| `bluedock-report` | 工作报告 | `api/report/*` |
| `bluedock-system` | 系统设置、License、LDAP 配置、导出入口 | `api/system/*`、`api/license/*` |
| `bluedock-search` | 搜索门面 | `api/search/*` |
| `bluedock-assistant` | AI 助手桥接 | `api/assistant/*` |
| `bluedock-realtime` | WebSocket `/ws`、本机会话、Kafka fanout 消费 | WS（非 REST） |
| `bluedock-worker-notify` | 通知投递 | Kafka 消费 |
| `bluedock-worker-index` | 搜索索引 | Kafka 消费 |
| `bluedock-boot` | 可执行装配、Flyway、种子数据 | 聚合 |

会议、签到、仪表盘等可挂在 `bluedock-user` / `bluedock-project` 或后续拆模块；以不跨事务乱调为原则。

## 依赖方向

```
boot → 各业务模块 → common
worker-* → common（+ 所需 client）
业务模块之间：禁止循环；跨域副作用 → Kafka
```

## 与文档 modules/ 映射

产品文档按功能目录（`docs/modules/project` 等）组织；一个产品模块可对应一个或多个 Maven 模块（如 project + task）。实现时以本表包边界为准，不以客户端页面边界拆 jar。

详见 [architecture.md](architecture.md)、[`.agents/rules/modules.md`](../../.agents/rules/modules.md)。
