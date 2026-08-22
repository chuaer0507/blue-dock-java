# 会议 — API

前缀 `api/users/meeting`。

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `open` | create / join，签发 Agora token；create 时可带 `userIds` 发会议卡片；响应含 `appId`/`agoraUserId`/`messages` | 已落地 |
| `link` | 生成分享链接（Redis TTL 6h） | 已落地 |
| `tourist` | 游客信息；参数 `touristId`（= Agora uid 字符串）；JSON 含 `agoraUserId`/`nickname`/`userImage` | 已落地 |
| `invitation` | 邀请成员：经 `meeting-alert` 机器人发 `type=meeting` 对话卡片；响应 `messages` | 已落地 |

鉴权：`open` / `link` / `tourist` 可选 Bearer；create 与无 shareKey 的 join/link 必须登录。游客 join 需有效 `shareKey`。

## 会议卡片

- 表：`bluedock_meeting_messages`（落库 `meeting_id` ↔ `dialog_id` / `message_id`；API wire `meetingId`/`dialogId`/`messageId`）
- 机器人：`meeting-alert@bot.system`（需 `bluedock.seed.enabled` 种子）
- 关房后合并 `endAt` 到卡片 JSON，并推送 WS `dialog.message.update`

## 自动关房

`CloseMeetingRoomScheduler`（boot `@EnableScheduling`）：

- 节流 Redis `bluedock:meeting:close:tick`（10 分钟）
- 选取 `end_at IS NULL` 且 `updated_at` 超过 `close-idle-minutes` 的会议
- 配置了 `api-key` + `api-secret` 时查询 Agora 频道是否为空；否则仅当 `allow-close-without-rest=true` 才关房

配置：`bluedock.meeting.*` YAML + `api/system/setting/meeting` 落库覆盖（见 [infra/meeting-agora.md](../../infra/meeting-agora.md)）。
