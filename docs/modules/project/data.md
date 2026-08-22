# 项目 — 数据模型

物理表前缀 `bluedock_`。总览见 [database.md](../../data/database.md)。

## projects（`bluedock_projects`）

| 列 | 类型 | 说明 |
| -- | ---- | ---- |
| id | BIGINT PK | 项目 ID |
| name | VARCHAR | 名称 |
| description | VARCHAR(500) | 描述 |
| userId | BIGINT | 创建人（物理列 `user_id`；API wire `userId`） |
| is_personal | TINYINT | 0 团队 · 1 个人（API wire `isPersonal`） |
| dialog_id | 项目群；团队项目自动创建，个人项目为 0 |
| archive_method / archive_days | system/custom；custom 时天数 1-365 |
| department_owner_view | 库 TINYINT（1 open / 0 close）；wire `open`/`close` |
| task_template_share | open/close | 跨项目模板共享；close 时仅本项目 |
| ai_auto_analyze | open/close | AI 自动分析 |
| user_simple | | 成员摘要缓存（可选） |
| archived_at / archived_user_id | | 归档 |
| created_at / updated_at / deleted_at | DATETIME(3) | |

## project_users（`bluedock_project_users`）

| 列 | 说明 |
| -- | ---- |
| project_id + user_id | UNIQUE |
| owner | 0 成员 · 1 拥有者（每项目唯一）· 2 管理员 |
| top_at | 本人置顶时间；NULL=未置顶 |
| sort | 本人项目列表顺序 ASC |

## project_columns（`bluedock_project_columns`）

| 列 | 说明 |
| -- | ---- |
| project_id | 所属项目 |
| name / color / sort | 列展示与排序 |
| deleted_at | 软删；`column/remove` 级联软删列内任务（含子任务），不自动迁移；至少保留一列 |

## project_permissions（`bluedock_project_permissions`，V1）

| 列 | 说明 |
| -- | ---- |
| project_id | UNIQUE |
| permissions | JSON：`project_member` / `task_leader` / `task_assist` → 权限点字符串数组 |

默认：`project_member` 含 ADD/UPDATE/STATUS/TIME/ARCHIVED/MOVE/LIST_SORT；`task_leader`/`task_assist` 含全部 11 点；LIST_ADD/UPDATE/REMOVE 默认仅管理侧。

## project_logs（`bluedock_project_logs`，V1）

| 列 | 说明 |
| -- | ---- |
| project_id / column_id / task_id | 作用域；子任务日志的 `task_id` 写主任务 id |
| task_only | 1=仅任务详情可见（项目动态排除） |
| user_id | 操作者；wire `userId` |
| detail | ≤500；含「任务/子任务」文案 |
| record | JSON 扩展（`change` / `userId` / `subtask` 等） |

## project_flows / project_flow_items（V1）

| 表 | 要点 |
| -- | ---- |
| `bluedock_project_flows` | `project_id` + `name`；软删 |
| `bluedock_project_flow_items` | `status`（start/progress/test/end）；`turns`（可流转目标 id，逗号分隔）；`user_ids` / `user_type`；`column_id` 可选绑定列；单流程 ≤10 节点 |

默认 5 节点：待处理 → 进行中 → 待测试 → 已完成 / 已取消（两终点均为 `end`）。

## 其它

- `project_invites`：邀请 code、过期
- `project_tags`：`bluedock_project_tags`（name≤20、color、sort；软删）
- `bluedock_task_tags`：任务-标签关联；单任务 ≤10；跨项目移动清空

状态：有效 / 已归档（`archived_at`）/ 已删除（`deleted_at`）。
