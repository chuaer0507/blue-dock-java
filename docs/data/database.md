# 核心表与关系

MySQL **9.7.2** · InnoDB · `utf8mb4_unicode_ci`。物理表统一 **`bluedock_` 前缀**（MyBatis-Plus `table-prefix: bluedock_`）。时间存 UTC `DATETIME(3)`。软删：`deleted_at` NULL = 有效。

命名与迁移规则见 [`.agents/rules/database.md`](../../.agents/rules/database.md)；**禁止简写**词表见 [naming.md](../contract/naming.md)。主键策略见 [id-generation.md](id-generation.md)。

首版表结构以 Flyway [`V1__init_core.sql`](../../bluedock-boot/src/main/resources/db/migration/V1__init_core.sql) 为准；下表为对照索引，列级细节见各模块 `data.md`。

## ER 总览

```
users ──┬── project_users ── projects ──┬── project_columns
        │                               ├── project_tasks ──┬── project_task_users
        │                               │                   ├── project_task_visibility_users
        │                               │                   └── dialogs (task)
        │                               ├── project_flows / flow_items
        │                               └── dialogs (project)
        ├── dialog_users ── dialogs ── dialog_messages
        ├── files ── file_contents / file_users / file_links
        ├── user_departments / user_department_owners
        ├── reports / report_receives
        └── favorites / devices / bots …
```

## 表清单（按域）

### 用户 / 组织

| 逻辑表 | 物理表 | 要点 |
| ------ | ------ | ---- |
| users | `bluedock_users` | PK `id`；`identity`；邮箱唯一；资料字段 `telephone`/`birthday`/`address`/`introduction`/`profession`/`lang` |
| auth_key_pairs | `bluedock_auth_key_pairs` | 登录 RSA 密钥；`keyId` 唯一；`status=active`；公钥缓存 Redis |
| user_deletes | `bluedock_user_deletes` | 注销申请 / 快照；邮箱 30 天保护 |
| user_departments | `bluedock_user_departments` | 部门树；`owner_user_id`；`dialog_id` |
| user_department_owners | `bluedock_user_department_owners` | 部门管理员（deputy） |
| user_department_members | `bluedock_user_department_members` | 部门成员 |
| user_devices | `bluedock_user_devices` | Token 设备；`hash`=md5(token)；`expired_at` |
| user_email_verifications | `bluedock_user_email_verifications` | 邮箱验证链接；`code` 唯一；`type` reg\|edit\|delete；30min |
| user_favorites | `bluedock_user_favorites` | 多态收藏；唯一 `(user_id, fav_type, ref_id)` |
| user_task_browses | `bluedock_user_task_browses` | 任务浏览历史；唯一 `(user_id, task_id)` |
| user_recent_items | `bluedock_user_recent_items` | 最近访问；唯一 `(user_id, target_type, target_id, source_type, source_id)` |
| user_bots | `bluedock_user_bots` | 用户自定义机器人（owner↔bot_id；`clear_day`/`clear_at`） |
| user_tags | `bluedock_user_tags` | 个性标签；贴在 `user_id`；`creator_user_id`；name≤20；软删 |
| user_tag_recognitions | `bluedock_user_tag_recognitions` | 认可；唯一 `(tag_id, user_id)` |
| user_attendance_* | `bluedock_user_attendance_records` / `_macs` / `_faces` | 签到记录 / MAC / 人脸；设置见 `attendanceSetting` |
| push_aliases | `bluedock_user_push_aliases` | App 推送别名（`/api/users/appPush/alias`） |
| app_push_logs | `bluedock_app_push_logs` | APP 推送投递日志（request/response/status/skip_reason） |

### 项目 / 任务

| 逻辑表 | 物理表 | 要点 |
| ------ | ------ | ---- |
| projects | `bluedock_projects` | `personal`；`dialog_id`；`archive_method`/`archive_days`；`ai_auto_analyze`；`department_owner_view`（默认 1）；`task_template_share`（默认 open）；归档 |
| project_users | `bluedock_project_users` | `owner`：0 成员 / 1 拥有者 / 2 管理员；`top_at` 本人置顶；`sort` 本人列表序 |
| project_columns | `bluedock_project_columns` | 看板列；`sort` ASC |
| project_permissions | `bluedock_project_permissions` | 权限矩阵 JSON |
| project_flows / flow_items | `bluedock_project_flows` · `bluedock_project_flow_items` | 工作流节点与 `turns` |
| project_tags | `bluedock_project_tags` | 项目内标签；name≤20 |
| project_invites | `bluedock_project_invites` | 邀请码 |
| project_logs | `bluedock_project_logs` | 操作日志；`task_only` 过滤 |
| project_tasks | `bluedock_tasks` | 主/子任务；`priority_*`（wire `priorityLevel`…）；`visibility`；`flow_item_id`；`loop`/`loop_at`；`archived_follow`；时间窗 |
| project_task_users | `bluedock_task_users` | `parent_task_id`；`owner`：负责人 vs 协助 |
| project_task_visibility_users | `bluedock_task_visibility_users` | visibility=3 |
| project_task_contents | `bluedock_task_contents` | 富文本详情；`content` HTML；`description` 短摘要 |
| project_task_files | `bluedock_task_files` | 任务附件元数据；`download_count`（API wire `download`） |
| project_task_tags | `bluedock_task_tags` | 任务-标签；单任务≤10 |
| project_task_templates | `bluedock_task_templates` | 任务模板；默认 / 排序 / 使用计数 |
| project_task_ai_events | `bluedock_task_ai_events` | AI 建议；`event_type`+`status`；`result` JSON；`message_id` |
| project_task_relations | `bluedock_task_relations` | 任务关联 |

### 会话 / 消息

| 逻辑表 | 物理表 | 要点 |
| ------ | ------ | ---- |
| dialogs | `bluedock_dialogs` | `type` / `group_type`；`owner_id`；`link_id` |
| dialog_users | `bluedock_dialog_users` | 成员；`unread`/`mention`/`mention_ids`；`is_top`/`is_hidden`/`is_muted`/`tag`（API wire top/hide/mute）；`mark_unread`；`color`（个人会话色） |
| dialog_messages | `bluedock_dialog_messages` | 消息体 `body`；`type`；`tag_user_id`（消息标注者）；软删字段 |
| dialog_message_reads | `bluedock_dialog_message_reads` | 已读回执；`is_silent` · `email_sent` · `dot`（红点） |
| dialog_message_emojis | `bluedock_dialog_message_emojis` | 表情回复 |
| dialog_message_tops | `bluedock_dialog_message_tops` | 消息置顶 |
| dialog_message_todos | `bluedock_dialog_message_todos` | 消息待办（索引含 `remind_at,done_at`） |
| dialog_message_translations | `bluedock_dialog_message_translations` | 消息翻译缓存；唯一键 `(message_id, language)` |
| dialog_configs | `bluedock_dialog_configs` | 会话级群禁言（`is_chat_muted`） |
| dialog_sessions | `bluedock_dialog_sessions` | 对话侧 AI 多会话（`session_key`/`title`；当前键在 `dialog_users.session_key`） |

> 代码与 API 统一称 **Dialog**。助手侧另见 `bluedock_ai_assistant_sessions`。

### 文件

| 逻辑表 | 物理表 | 要点 |
| ------ | ------ | ---- |
| files | `bluedock_files` | 树 `parent_id`；`created_user_id`；`hash` 秒传；`is_shared`；`path` |
| file_contents | `bluedock_file_contents` | 在线内容版本链 |
| file_users | `bluedock_file_users` | 共享成员 `permission` 0/1 |
| file_links | `bluedock_file_links` | 公开链接 `code` / `allow_guest` |
| upload_objects | `bluedock_upload_objects` | 系统/管理端上传库；见 [upload-objects.md](../infra/upload-objects.md) |

### 报告 / 会议 / 系统

| 逻辑表 | 物理表 | 要点 |
| ------ | ------ | ---- |
| reports / receives / links / ai_analyses | `bluedock_reports` · `bluedock_report_receives` · `bluedock_report_links` · `bluedock_report_ai_analyses` | 日报周报；收件；分享短码；按查看者 AI 解读 |
| meetings / meeting_messages | `bluedock_meetings` · `bluedock_meeting_messages` | Agora 会议；卡片消息关联 |
| settings | `bluedock_settings` | 键值 / 分组 JSON：`oss` · `fileSetting` · `emailSetting` · `aiBotSetting` · `meetingSetting` · `appPushSetting` … |
| complaints | `bluedock_complaints` | 会话举报；硬删 |
| app_badges | `bluedock_app_badges` | 微应用角标 |
| user_app_sorts | `bluedock_user_app_sorts` | 个人应用排序 JSON |
| installed_apps | `bluedock_installed_apps` | 应用市场注册表（无 Docker；含 `version`） |
| ai_assistant_* | `bluedock_ai_assistant_sessions` · `bluedock_ai_assistant_feedbacks` · `bluedock_ai_assistant_search_logs` | 助手会话 / 反馈 / 检索日志 |
| outbox | `bluedock_outbox` | 事务内入队；boot `OutboxPoller` 投递 Kafka；见 [messaging.md](../architecture/messaging.md) |
| search_docs | `bluedock_search_docs` | 搜索增量文档（引擎前过渡表） |

## 关键字段约定

| 概念 | 字段 | 取值 |
| ---- | ---- | ---- |
| 项目角色 | `project_users.owner` | 0 / 1 / 2 |
| 任务角色 | `bluedock_users.owner` | 1 负责 · 0 协助 |
| 任务可见性 | `tasks.visibility` | 1 项目 · 2 任务人员 · 3 指定 |
| 会话类型 | `dialogs.type` | `user` / `group` |
| 群子类 | `dialogs.group_type` | `user` / `project` / `task` / `department` / `all` / `okr` |
| 个人项目 | `projects.is_personal`（wire `isPersonal`） | 0 团队 · 1 个人 |

## 索引建议（首版）

- `project_users (project_id, user_id)` UNIQUE
- `tasks (project_id, column_id, sort)`；`(parent_id)`；`(end_at)` 仪表盘/日历
- `dialog_messages (dialog_id, id)`；`(dialog_id, created_at)`
- `files (user_id, parent_id)`；`(hash, user_id)` 秒传
- `users (email)` UNIQUE

## 模块细表

| 模块 | data.md |
| ---- | ------- |
| 项目 | [modules/project/data.md](../modules/project/data.md) |
| 任务 | [modules/task/data.md](../modules/task/data.md) |
| 即时通讯 | [modules/messenger/data.md](../modules/messenger/data.md) |
| 文件 | [modules/file/data.md](../modules/file/data.md) |
| 用户账号 | [modules/user-account/data.md](../modules/user-account/data.md) |

## Flyway

- 路径：`bluedock-boot/src/main/resources/db/migration/V{n}__*.sql`
- **开发期**（版本 ≤ `1.0.0-SNAPSHOT`）：可直接改当前 Vn（本地库需重建）
- **上生产后**：只追加 additive `V{n+1}`，禁止改已发布脚本
