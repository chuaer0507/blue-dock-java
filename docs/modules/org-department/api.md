# 部门 — API / 数据 / 权限

## API（`api/users/department*`）

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `department/list` · `add` · `delete` | 列表 / 新建修改 / 删除（管理员） | 已落地 |
| `department/addDeputy` · `deleteDeputy` | 任命 / 罢免部门管理员 | 已落地 |
| `department/sync` | 子部门成员合并到当前部门 | 已落地（跳过禁用/机器人；`skippedDisabledCount`） |
| `info/departments` · `info/managedDepartments` | 我的部门 / 可管理的部门 | 已落地 |

## 数据

| 表 | 要点 |
| -- | ---- |
| `bluedock_user_departments`（V1） | 部门树；`owner_user_id`；`dialog_id`（经 `DepartmentGroupBridge` 建群） |
| `bluedock_user_department_owners`（V1） | 部门管理员 |
| `bluedock_user_department_members`（V1） | 成员归属（替代 users.department 逗号串） |

## 权限

- 部门 CRUD / 任命：仅系统管理员
- 负责人视角：只读扩大可见范围，不提升项目内编辑权（`dashboard/team/*?departmentId=` 已接）

详见 [overview.md](overview.md)。
