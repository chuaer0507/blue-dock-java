# 仪表盘 — API

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `api/dashboard/team/stats?departmentId=` | 负责人视角统计 | 已落地（部门树） |
| `api/dashboard/team/tasks?departmentId=` | 负责人视角任务列表（type/memberId/level） | 已落地（部门树） |

**团队范围**：

- 传 `departmentId`：须为当前用户可管理的部门（owner/deputy）；成员 = 该部门及下级在职非机器人；项目 = 这些成员参与且 `department_owner_view=1` 的未归档项目。
- 不传 `departmentId`：兼容回退为当前用户项目 `owner≥1` 的未归档项目及其成员。

任务口径见 [overview.md](overview.md)（主任务、全员可见、未归档）。

个人视角「今日 / 超期 / 待办 / 协助 / 本周完成」等可由前端基于 `api/project/user/tasks`、`user/counts` 聚合：

| URL | 说明 |
| --- | ---- |
| `GET /api/project/user/counts?userId=` | `{project,todo,done}`；可选 `owner=0|1` |
| `GET /api/project/user/tasks?userId=` | 分页任务；可选 `owner` · `projectId` · `keys={name,status}` |

**权限**：本人 / 系统管理员全量；部门负责人仅当系统 `departmentOwnerProjectView=open`、目标在管理树内，且限定 `department_owner_view=1` 项目与 `visibility=1` 任务（`departmentReadonly=true`）。
