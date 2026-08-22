# 任务 — 数据模型

物理表：`bluedock_tasks`（逻辑名 tasks / project_tasks）。总览见 [database.md](../../data/database.md)。

## tasks（`bluedock_tasks`）

| 列 | 说明 |
| -- | ---- |
| id | BIGINT PK |
| parent_id | 0=主任务；>0=子任务 |
| project_id / column_id | 所属项目与列 |
| dialog_id | 任务群；`GET /api/project/task/dialog` 按需创建 |
| flow_item_id / flow_item_name | 工作流节点；改名不回填历史快照 |
| name / color / desc | 标题等 |
| start_at / end_at | 计划时间（日历数据源） |
| complete_at | 完成时间；非空视为完成 |
| visibility | 1 项目人员 · 2 任务人员 · 3 指定成员 |
| priority_level / priority_name / priority_color | 优先级（API wire `priorityLevel`/`priorityName`/`priorityColor`） |
| sort | 列内排序 ASC |
| loop / loop_at | 循环周期与下一截止；见 [recurring.md](recurring.md) |
| archived_at / archived_user_id / archived_follow | 归档 |
| user_id | 创建人（API wire `userId`） |
| deleted_at / deleted_user_id | 软删 |

## bluedock_users（`bluedock_task_users`）

| 列 | 说明 |
| -- | ---- |
| task_id / parent_task_id / project_id | 子任务时 parent_task_id 指向父任务（根任务可等于自身） |
| user_id | |
| owner | 1 负责人 · 0 协助 |

## 可见性 / 内容 / 附件

| 表 | 说明 |
| -- | ---- |
| `bluedock_task_visibility_users` | visibility=3 的显式成员（`task_id`+`user_id` UNIQUE）；子任务不单独存，读时用父任务名单 |
| `bluedock_task_contents`（V1） | 长描述与历史（`content` HTML；每次 `update?content=` 追加一行） |
| `bluedock_task_files` | 附件；`download_count`（API wire `download`） |
| `bluedock_task_tags` | 任务-标签关联；`update?tagIds=` 全量替换 |
| `bluedock_task_templates`（V1） | 任务模板；`is_default` / `use_count` / `last_used_at` |
| `bluedock_task_relations`（V1） | 任务双向关联（`mention` / `mentioned_by`） |
| `bluedock_task_ai_events`（V1） | AI 建议事件；`event_type`=description/subtasks/assignee/similar；`status`=pending/processing/completed/failed/skipped/applied/dismissed |

## 状态机（简）

```
创建 →（列内/工作流流转）→ 完成(complete_at)
                      ↘ 归档(archived_at)
                      ↘ 删除(deleted_at) → 可恢复（回收策略）
```

子任务：可见性强制继承父任务；时间默认落在父任务窗内（产品规则）。
