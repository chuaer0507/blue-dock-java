# 项目 — 权限

## 项目角色（ProjectUser.owner）

| 值 | 角色 | 要点 |
| -- | ---- | ---- |
| 1 | 拥有者 / 负责人 | 每项目唯一；可转让、归档/删除、任命管理员 |
| 2 | 管理员 | 可改设置、成员、工作流/列/标签；不可转让拥有者、不可罢免其他管理员 |
| 0 | 普通成员 | 任务操作受权限矩阵约束 |

与系统管理员、部门角色**互不继承**。

## 任务权限点（ProjectPermission）

主体：`project_leader`（隐式全开）· `project_member` · `task_leader` · `task_assist`

| 权限点 | 含义 |
| ------ | ---- |
| TASK_LIST_ADD / UPDATE / REMOVE / SORT | 列操作 |
| TASK_ADD / UPDATE / TIME / STATUS / REMOVE / ARCHIVED / MOVE | 任务操作 |

- 拥有者 / 管理员：**不可被矩阵限制**（始终全开）
- 默认：普通成员可加改任务、改状态/时间、归档、移动、列排序；加/改/删列仅管理侧
- API：`GET /api/project/permission` · `GET /api/project/permission/update?permissions=`
- 运行时：列 CRUD、看板排序、任务增改删归档移动已按矩阵校验
- **不支持**：按列/标签细分；按部门角色自动套权限

## 个人项目 vs 团队项目

| | 团队 `isPersonal=0` | 个人 `isPersonal=1` |
| -- | ---------------- | ----------------- |
| 成员 | 可邀请 | 仅自己，每用户限 1 |
| 群聊 | 自动建项目群 | 无协作群 |
| 工作流 / 权限矩阵 | 有 | 无 |
| 转让 | 有 | 无（特殊处理见产品说明） |

详见 [overview.md](overview.md)。
