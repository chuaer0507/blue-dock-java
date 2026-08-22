# 邮件 SMTP（管理端可配 · 数据库）

对齐系统设置里的 **文件 / OSS** 管理方式：管理员在「系统设置 → 邮件通知」配置 SMTP 与业务开关，**写入 `bluedock_settings.name=emailSetting`**，Worker / 测试发信读库；**不**按客户端形态拆配置、不以纯 env 为权威源。总览见 [admin-db-settings.md](admin-db-settings.md)。

**实现状态**：**已落地**（设置 API + Worker SMTP + 未读汇总调度 `UnreadEmailNoticeService`；见 §5）。

**关联**：[notify/overview](../modules/notify/overview.md) · [system-setting/api](../modules/system-setting/api.md) · [oss-settings.md](oss-settings.md) · [admin-db-settings.md](admin-db-settings.md) · [messaging.md](../architecture/messaging.md) · [api-contract.md](../contract/api-contract.md)

---

## 1. 目标与范围

### 1.1 目标

- 管理员配置 **SMTP 连通参数**（主机、端口、账号、密码、SSL、发件人）与 **业务开关**（注册验证、未读汇总、忽略地址等）。
- 与 `fileSetting` / `oss` 相同模式：`bluedock_settings` 分项 JSON、仅系统管理员读写、`SYSTEM_SETTING=disabled` 禁写。
- GET **掩码** `smtpPassword`（已设则为 `********`）；POST 密码为空或 `********` 时 **保留原密文**（与 OSS 密钥规则一致）。
- 异步投递走 Kafka `bluedock.notify.send`（`channel=email`）；管理员「测试发送」同步调 SMTP，不经 Kafka。

### 1.2 已生效

| 能力 | 状态 |
| ---- | ---- |
| 全字段读写持久化（`emailSetting`） | ✅ |
| GET 密码掩码 / POST 保留密文 | ✅ |
| `email/check` 同步测试发信 | ✅ |
| Worker `SmtpMailClient` 读 DB 配置 | ✅ |
| 未配 SMTP 时静默跳过投递 | ✅ |
| `ignoreAddr` 过滤 | ✅ |
| 用户侧关闭邮件 / 未读汇总调度 | ✅ 会话免打扰 → `silence`；调度见 §5 |

### 1.3 非目标

- 不按客户端拆分 SMTP（全局一套）。
- 不在本设置项存多套发件人模板引擎；正文由各业务事件载荷决定。
- 不把 Redis 当邮件队列（必须 Kafka）。
- 不提供普通用户「一键退订未读邮件」开关（靠管理员 `ignoreAddr` / 会话免打扰）。

---

## 2. 管理端 API

与上传类设置同挂 `api/system`：

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/system/setting/email` | 管理员 | 当前配置；`smtpPassword` 已设则 `********` |
| POST | `/api/system/setting/email` | 管理员 | 保存；密码空或 `********` 保留原值；受 `SYSTEM_SETTING=disabled` 禁写 |
| GET | `/api/system/email/check` | 管理员 | 测试发信；`email=` 收件地址；→ `{ok,to}` |

> 一接口一路径；勿为 email 设置再注册 PUT 别名。

### 2.1 JSON 形态（camelCase）

存 `bluedock_settings.name = emailSetting`：

```json
{
  "smtpHost": "smtp.example.com",
  "smtpPort": "465",
  "smtpUsername": "noreply@example.com",
  "smtpPassword": "********",
  "smtpSsl": "open",
  "fromAlias": "BlueDock",
  "fromAddress": "noreply@example.com",
  "ignoreAddr": "bot@example.com,test@example.com",
  "regVerify": "close",
  "noticeMessage": "close",
  "messageUnreadTimeRanges": [["00:00", "09:00"], ["18:00", "23:59"]],
  "messageUnreadUserMinute": 30,
  "messageUnreadGroupMinute": 60
}
```

| 字段 | 说明 |
| ---- | ---- |
| `smtpHost` / `smtpPort` / `smtpUsername` / `smtpPassword` | SMTP 连通；端口默认 `465` |
| `smtpSsl` | `open`（默认，SSL）/ 其他 → STARTTLS |
| `fromAlias` / `fromAddress` | 发件显示名与地址（空地址时可用账号兜底，见客户端实现） |
| `ignoreAddr` | 永不收信的邮箱，逗号/分号/空白分隔，或 JSON 数组 |
| `regVerify` | `open`/`close`：注册等需邮箱验证 |
| `noticeMessage` | `open`/`close`：未读消息汇总邮件 |
| `messageUnreadTimeRanges` | 汇总允许时段（服务器本地 `H:mm`）；空数组则永不发 |
| `messageUnreadUserMinute` / `messageUnreadGroupMinute` | 单聊/群聊未读满 N 分钟才汇入；`-1` 表示该类型不发 |

实现：`EmailSettingService` · `EmailSettingMaps` · `SmtpMailClient` · `UnreadEmailNoticeService`。

---

## 3. 与上传配置的对照

| 维度 | 上传 / OSS | 邮件 SMTP |
| ---- | ---------- | --------- |
| 管理员入口 | `setting/file` · `setting/oss` | `setting/email` |
| 存储 | `fileSetting` · `oss` | `emailSetting` |
| 密钥策略 | GET `********`；POST 空/掩码保留 | 同左（`smtpPassword`） |
| 禁写 | `SYSTEM_SETTING=disabled` | 同左 |
| 运行时消费 | `RuntimeObjectStorage` / 上传入口 | Worker SMTP + `email/check` + 未读汇总调度 |
| 业务正交项 | `uploadMaxMb` vs 引擎 | SMTP 连通 vs `regVerify`/`noticeMessage` |

产品场景与边界见 [notify/overview.md](../modules/notify/overview.md)。

---

## 4. 投递链路

```
业务事件 → Kafka bluedock.notify.send（channel=email）
         → bluedock-worker-notify / EmailNotifyChannel
         → SmtpMailClient（读 emailSetting）
```

- 未配置 SMTP（host/账号等不全）：静默跳过（debug 日志）
- 命中 `ignoreAddr` / 禁用账号 / 机器人 / 空邮箱：不发
- 管理员测试：`GET /api/system/email/check?email=` 同步发送，失败抛 `email.send_failed`

典型场景：注册验证码、改邮验证、未读汇总、会议邀请等（以开关为准；业务侧经 `NotifySendPublisher` 发事件）。

---

## 5. 未读消息汇总调度（已落地）

未读汇总调度：

| 项 | 约定 |
| -- | ---- |
| 触发 | `UnreadEmailNoticeScheduler`（默认 5 分钟，`bluedock.email.unread-notice-ms`）；Redis 互斥 `bluedock:email:unread:notice:tick` |
| 开关 | `noticeMessage=open` + SMTP 已配 + 当前时刻落在 `messageUnreadTimeRanges` |
| 水位 | `bluedock_settings.name=emailLastNotice`：`{timeUser,timeGroup}` |
| 消息类型 | `text` / `file` / `record` / `meeting` |
| 过滤 | `read_at IS NULL` · `email=0` · `silence=0`（会话免打扰写入） |
| 分钟阈值 | 单聊 `messageUnreadUserMinute` / 群聊 `messageUnreadGroupMinute`；`-1` 跳过该类型 |
| 投递 | 按用户发 `NotifySendEvent`（`data.kind=unreadDigest` + `messageReadIds`） |
| 标记 | Worker 发信成功后 `bluedock_dialog_message_reads.email=1` |
| 用户关闭 | **无**独立退订；免打扰 → `silence`；管理员 `ignoreAddr` |

正文为纯文本摘要（后续可增强为 HTML 模板）。
