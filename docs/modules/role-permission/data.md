# 角色与权限 — 数据

角色不落「统一权限表」，按层级分表。

## 系统身份

| 表 / 字段 | 要点 |
| -------- | ---- |
| `bluedock_users.id` | `id=1` 为超级管理员（唯一，不可降级） |
| `bluedock_users.identity` | JSON/数组语义：`admin` / `temporary` / `disable` / `ldap` / `system` / `bot` … |
| `bluedock_users.disable_at` | 离职时间 |

## 部门

| 表 | 要点 |
| -- | ---- |
| `bluedock_user_departments` | `owner_user_id` 部门负责人（每部门一人） |
| `bluedock_user_department_owners` | 部门管理员（可多人） |
| `bluedock_user_department_members` | 成员归属 |

## 项目 / 任务

| 表 | 要点 |
| -- | ---- |
| `bluedock_project_users` | `owner`：1 负责人 / 2 管理员 / 0 成员 |
| `bluedock_project_permissions` | 项目权限矩阵 JSON |
| `bluedock_task_users` | 任务负责（owner=1）/ 协助（owner=0） |
| `bluedock_task_visibility_users` | visibility=3 时可见名单 |

## 不建表

- 无全局 RBAC 角色自定义表
- 无「离职交接」独立表（走 `UserDisableHandoverBridge` 事务内改归属）

详见 [overview.md](overview.md) · [database.md](../../data/database.md)。
