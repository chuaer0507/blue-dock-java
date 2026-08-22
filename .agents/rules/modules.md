---
description: Maven 模块边界与包结构
globs: "**/bluedock-*/**"
alwaysApply: false
---

# 模块规则

模块清单见 [docs/architecture/services.md](../../docs/architecture/services.md)（待写时以下表为准）。

## 仓库结构（目标）

```
BlueDock/
├── pom.xml
├── bluedock-common/            # ResultModel、异常码、Redis/Kafka 常量、IdGenerator
├── bluedock-auth/              # 登录、JWT、验证码、设备
├── bluedock-user/              # 用户资料、设置、收藏
├── bluedock-org/               # 部门、角色权限
├── bluedock-project/           # 项目、列、工作流、权限
├── bluedock-task/              # 任务、子任务、模板、标签
├── bluedock-messenger/         # 会话、消息
├── bluedock-file/              # 文件、分片上传元数据
├── bluedock-report/            # 工作报告
├── bluedock-system/            # 系统设置、License、LDAP 配置
├── bluedock-search/            # 搜索门面
├── bluedock-assistant/         # AI 助手
├── bluedock-realtime/          # WebSocket
├── bluedock-worker-notify/     # 通知投递 Worker（无 HTTP）
├── bluedock-worker-index/      # 搜索索引 Worker（无 HTTP）
├── bluedock-boot/              # 可执行 JAR；db/migration、db/seed
├── docs/
├── deploy/
├── .agents/
├── AGENTS.md
└── .claude/
```

实际落地可按阶段裁剪；新增模块须在父 `pom.xml` 注册，并在 `bluedock-boot` 引入。

## 模块职责边界

| 模块 | 可以 | 不可以 |
| ---- | ---- | ------ |
| `bluedock-project` | 项目/列/工作流 CRUD、成员权限 | 直接推 WS / 发邮件 |
| `bluedock-messenger` | 会话消息落库、读扩散 | 用 Redis List 广播事件 |
| `bluedock-worker-*` | 消费 Kafka、调外部通道 | 暴露 REST |
| `bluedock-realtime` | WS 会话、心跳、按事件推送 | 复杂业务状态机 |

## 包命名

`com.bluedock.{module}.{layer}` — 如 `com.bluedock.project.service`、`com.bluedock.messenger.mapper`。
