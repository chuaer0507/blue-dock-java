# 即时通讯 — 数据模型

物理表：`bluedock_dialogs` / `bluedock_dialog_*`（不再使用 `web_socket_*` 前缀）。总览见 [database.md](../../data/database.md)。

## dialogs（`bluedock_dialogs`）

| 列 | 说明 |
| -- | ---- |
| id | BIGINT PK |
| type | `user` 单聊 · `group` 群 |
| group_type | `user` / `project` / `task` / `department` / `all` / `okr` |
| name / avatar | 群名与头像 |
| owner_id | 群主 |
| link_id | 关联项目/任务/部门等 |
| session_id | 遗留列（可选；对话侧 AI 当前键见 `dialog_users.session_key`） |
| last_at | 最后消息时间（列表排序） |
| deleted_at | 软删 / 解散 |

消息置顶见附属表 `bluedock_dialog_message_tops`（非 dialogs 列）。

## dialog_users（`bluedock_dialog_users`）

| 列 | 说明 |
| -- | ---- |
| dialog_id + user_id | 成员 |
| is_deputy | 群管理员 |
| session_key | 当前用户在该会话的 AI 会话键（`dialog/session/*`） |
| 扩展 | `unread_count` · `mention_count` · `mention_ids` · `last_read_message_id` · `is_top` · `is_hidden` · `is_muted` · `tag` · `is_deputy` · `session_key`（API wire camelCase） |

## dialog_messages（`bluedock_dialog_messages`）

| 列 | 说明 |
| -- | ---- |
| dialog_id / user_id | 会话与发送者（API wire 仍称 userId；含机器人） |
| session_key | AI 单聊消息所属会话键；普通会话为空。`message/list` 按当前用户的 `dialog_users.session_key` 过滤，确保 AI 多会话隔离。 |
| type | text / file / image / vote / wordChain / meeting / … |
| body | JSON/长文本载荷（API wire 字段名仍常为 `body`）；vote/wordChain 状态内嵌（见 [api.md](api.md)） |
| read / send | 已阅人数 / 应达人数（可演进为回执表） |
| 撤回 / 删除 | 标记字段或软删 |

## 附属表

| 表 | 用途 |
| -- | ---- |
| `bluedock_dialog_message_reads` | 已读回执；`silence`（免打扰）· `email`（未读汇总已发）；发消息时预插成员行 |
| `bluedock_dialog_message_emojis` | 表情回复（V1 已建） |
| `bluedock_dialog_message_tops` | 消息置顶（V1 已建；可多条） |
| `bluedock_dialog_message_todos` | 消息待办与提醒（V1 已建） |
| `bluedock_dialog_configs` | 会话级配置：`is_chat_muted` 群禁言（与个人免打扰 `dialog_users.is_muted` 区分） |
| `bluedock_dialog_sessions` | 对话侧 AI 多会话（`session_key`/`title`；当前会话在 `dialog_users.session_key`） |

## 与项目 / 任务

- 项目创建 → 写 `dialogs(group_type=project)` → `projects.dialog_id`
- 任务开聊 → 写 `dialogs(group_type=task)` → `tasks.dialog_id`
- 成员变更同步 `dialog_users`
