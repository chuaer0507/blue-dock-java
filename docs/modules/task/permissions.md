# 任务 — 权限与可见性

## 任务角色

| 角色 | 说明 |
| ---- | ---- |
| 负责人 | TaskUser leader |
| 协作者 | TaskUser assist |
| 可见用户 | `visibility=3` 时显式列表 |

## 可见性 visibility

| 值 | 含义 |
| -- | ---- |
| 1 | 项目人员可见（默认） |
| 2 | 仅任务人员（负责+协助） |
| 3 | 指定成员（负责+协助+可见用户表 `bluedock_task_visibility_users`） |

- 子任务**强制继承**父任务可见性，不可单独设；指定名单只挂在主任务上
- 列表 / 详情 / 子任务列表均按当前用户过滤：不可见视为不存在（`task.not_found`）
- 项目管理员**不会**自动看到 visibility=2/3，除非在任务人员/可见列表中
- 可见性只控制「能否看见」；编辑仍看 TASK_UPDATE 等权限点
- Wire：`visibilityUserIds`（兼容 `visibility_userIds`），逗号分隔，最多 100；须为项目成员
- **任务群**：成员集合与上表一致；messenger 对 `group_type=task` 再经 `TaskDialogAccessBridge` 校验，不可见则拒绝读写/列表隐藏
## 与项目权限关系

任务增删改移归档等，先过项目角色 + `ProjectPermission` 矩阵，再结合任务角色。见 [project/permissions.md](../project/permissions.md)。

详见 [overview.md](overview.md)。
