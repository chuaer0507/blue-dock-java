# 机器人 — API

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `GET api/users/userBot/list` | 当前用户自建机器人列表 | 已落地 |
| `GET api/users/userBot/info` | 详情；系统机器人仅管理员 | 已落地 |
| `POST api/users/userBot/edit` | 新建 / 编辑（webhook、clearDay） | 已落地 |
| `GET api/users/userBot/delete` | 删除自建机器人（需 remark） | 已落地 |

表：`bluedock_user_bots`。系统机器人种子：`*@bot.system`（`bluedock.seed.enabled`）。

## Webhook 投递（已落地）

| 环节 | 说明 |
| ---- | ---- |
| 触发 message | `DialogService.sendText`：会话内 `bot=1` 且订阅 `message`；发送方为机器人或 `/` 指令跳过 |
| 触发 memberJoin / memberLeave | `groupAddUser` / `groupDelUser`（含自行退群） |
| 触发 dialogOpen | `DialogService.one`；Redis 节流约 1 分钟/`dialogId+userId` |
| 总线 | Kafka `bluedock.userBot.webhook` → `bluedock-worker-notify` |
| HTTP | `application/x-www-form-urlencoded` POST，超时 30s，失败不重试；成功后 `webhook_count++` |
| 回复 | 仅 `message` 且响应 `{"code":200,"message":"..."}` → `bluedock.userBot.webhook.reply` → 机器人发文本 |

字段（form camelCase）：`event`/`text`/`replyText`/`token`/`dialogId`/`dialogType`/`groupType`/`dialogName`/`messageId`/`messageUserId`/`mention`/`botUserId`/`messageUser`/`member`/`operator`/`extras`/`version`（`1.0.0`）/`timestamp`。
