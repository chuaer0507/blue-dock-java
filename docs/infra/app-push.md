# APP 推送

产品场景见 [modules/notify/overview.md](../modules/notify/overview.md)。

## 绑定

- 客户端上报 → `api/users/appPush/alias`（**已落地**；`action=remove` 删除）
- 表：`bluedock_user_push_aliases`（user_id ↔ device token / alias；厂商无关落库名）
- 平台：请求头 / 参数 `platform` = `ios` \| `android`

## 配置（`appPushSetting`）

存 `bluedock_settings.name = appPushSetting`。与 OSS / SMTP / aiBot / meeting 同类管理员可配：

| Method | Path | 说明 |
| ------ | ---- | ---- |
| GET | `/api/system/setting/appPush` | 当前配置；`iosKey`/`iosSecret`/`androidKey`/`androidSecret` 已设则 `********` |
| POST | `/api/system/setting/appPush` | 保存；密钥空或 `********` 保留原值；`SYSTEM_SETTING=disabled` 禁写 |

字段：`open`、`iosKey` / `iosSecret`、`androidKey` / `androidSecret`、`aliasType`（默认 `bluedock`；须与客户端友盟 alias_type 一致）、`productionMode`。Worker 经 `loadRaw()` 读原文。

## 投递

```
业务事件 → Kafka bluedock.notify.send（channel=push）
         → bluedock-worker-notify / AppPushChannel
         →（PC 在线）Redis ZSET 延时 10s → 复查已读
         → AppPushClient（customizedcast + MD5 sign → msgapi.umeng.com）
         → bluedock_app_push_logs
```

上游 HTTP 仍走友盟开放接口；本仓领域命名统一为 **appPush**。

## 规则（已落地）

- 总开关 `open`、无 key/secret：跳过
- **触发**：会话 `dialog.message` 落库后（`DialogAppPushNotifyService`）发 `bluedock.notify.send` channel=`push`；`notice`/`template` 弱提醒与 `isSilent`/`silence` 跳过
- 别名：30 天内活跃、`is_notified=1`；每用户每平台最多 5 个
- **免打扰**：会话 `is_muted` / 读回执 `is_silent` → `skipped`/`muted`；**@提及强制推送**（`mentioned=true`，并清零该用户 `is_silent`）
- **PC 在线**（`bluedock:pc:active:{userId}`，由桌面 WS `client=desktop|electron|…` 注册/ping 写入）：入 **10 秒延时队列**；到期后若 `messageId` 已读则跳过，否则推送
- badge：iOS `aps.badge`；Android `set_badge`（≤99；按用户未读总和）
- 投递结果写入 `bluedock_app_push_logs`（`status`=`sent`/`failed`/`delayed`/`skipped`）

## 延时队列（Redis）

| Key | 类型 | 说明 |
| --- | ---- | ---- |
| `bluedock:appPush:delay:queue` | ZSET | score=到期 epoch ms；member=jobId |
| `bluedock:appPush:delay:job:{jobId}` | String(JSON) | 载荷；TTL 1h |
| `bluedock:appPush:delay:tick` | String | 轮询互斥 ~2s |

`AppPushDelayScheduler` 默认每 2s 拉取到期任务（`bluedock.app-push.delay-poll-ms`）。ZSET 仅作 Worker 内延时调度，**不是**跨域业务 MQ。

事件 `data` 带 `messageId` / `dialogId` / `badge`（可选 `mentioned`）。

## 日志表 `bluedock_app_push_logs`

| 列 | 说明 |
| -- | ---- |
| user_id / platform / alias | 目标 |
| title / body | 推送文案 |
| request_body / response_body | 上游请求与响应（截断） |
| status | `sent` · `failed` · `delayed` · `skipped` |
| skip_reason | `pc_active` · `already_read` · `muted` · `silence` |
| event_id / message_id / dialog_id | 关联 |