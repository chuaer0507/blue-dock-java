# 通知 — 验收清单

## email-notice / push-notice

- [x] SMTP 设置 + 测试发送 + 未读汇总（silence 过滤）
- [x] APP 推送设置 + Alias + Worker customizedcast；会话消息触发 push；免打扰/silence 过滤（@提及强制）；PC 在线 10s 延时 + 已读跳过；`bluedock_app_push_logs`

## desktop-notify（客户端本地）

- [x] 开关 / OS 权限 / 弹窗：Electron / Web Notification（**不**走服务端投递）
- [x] 新消息触发：WebSocket 下行；服务端 `CHANNEL_DESKTOP` 仅系统事件占位（Worker 打日志）
- [x] 会话免打扰：`dialog` mute / silence（屏蔽推送与邮件，不屏蔽 WS）

## mobile-notify

- [x] 权限同步：`GET/POST /api/users/appPush/alias`（`isNotified` → `bluedock_user_push_aliases`）
- [x] 推送开关：别名注册 / 移除 + 会话 `isMuted` / `silence`（无独立「全局关推送」API）
- [x] 时段静音：**客户端本地**（与 user-settings 一致；**无**云端时段偏好 API）

## 明确不做（后端）

- [x] 无按端拆 `desktop`/`mobile` Maven 模块
- [x] 无全局时段免打扰云端配置表

详见 [api.md](api.md) · [overview.md](overview.md) · [app-push.md](../../infra/app-push.md)。
