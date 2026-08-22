# 通知 — API

按通道汇总；契约唯一源仍为 `docs/contract/api-contract.md`。

## 邮件

| URL | 说明 |
| --- | ---- |
| `GET\|POST /api/system/setting/email` | 管理员 SMTP（密码掩码） |
| 相关用户侧 | 注册/改邮/注销验证码；未读汇总由 Worker 调度 |

见 [infra/email.md](../../infra/email.md)。

## APP 推送

| URL | 说明 |
| --- | ---- |
| `GET\|POST /api/system/setting/appPush` | 管理员推送密钥（掩码） |
| `GET /api/users/appPush/alias` | 设备别名 + `isNotified` 权限位 |
| （内部）Worker | `NotifySendEvent` → `AppPushChannel`；会话消息由 `DialogAppPushNotifyService` 触发；免打扰/`silence` 过滤；PC 在线延时 10s + 已读跳过；`bluedock_app_push_logs` |

见 [infra/app-push.md](../../infra/app-push.md)。

## 会话免打扰

| URL | 说明 |
| --- | ---- |
| `dialog` 配置 mute | 会话级免打扰 |
| `GET /api/dialog/message/silence` | 消息 silence 标记读写 |

影响邮件汇总与 APP 推送过滤；**不**屏蔽 WebSocket 站内消息。

## 桌面通知

无独立 REST。客户端消费 WS / 本地 `Notification`。系统事件可发 `NotifySendEvent.CHANNEL_DESKTOP`（Worker 不投递 OS 通知）。

## 时段静音

无后端 API；移动端本地实现。若未来需多端同步，另开用户偏好表切片。
