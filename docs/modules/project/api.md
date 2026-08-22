# 项目 — API

前缀 `api/project`。完整表见 [api-contract.md](../../contract/api-contract.md)。

## 已实现（骨架）

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET /api/project/lists` | Bearer | 当前用户参与的项目；`archived?=no\|yes\|all`（默认 no；`includeArchived=true` 等同 all）· `type?=all\|team\|personal` · `name?` 或 `keys` JSON（`{"name":"..."}`）按名称模糊搜 |
| `GET /api/project/one` | Bearer | 详情（须为成员）；`projectId` |
| `GET /api/project/add` | Bearer | 创建；`name` · `description?` · `isPersonal?`（1=个人，每用户限 1）· `columns?`（逗号分隔列名，常来自系统列模板；空则默认三列）；自动建拥有者成员 |
| `GET /api/project/update` | Bearer | 修改；`projectId` · `name?` · `description?` · `archiveMethod?`（system/custom）· `archiveDays?`（custom 时 1-365）· `aiAutoAnalyze?` · `taskTemplateShare?` · `departmentOwnerView?`（后三者 open/close）；须管理权限 |
| `POST /api/project/user` | Bearer | 加/删成员；`userIds` / `removeUserIds`（逗号分隔）；须管理权限；个人项目不可用 |
| `GET /api/project/invite` | Bearer | 获取或创建邀请码（30 天有效）；须管理权限 |
| `GET /api/project/invite/info` | **匿名** | 按 `code` 查看项目摘要 |
| `GET /api/project/invite/join` | Bearer | 凭 `code` 加入为普通成员 |
| `GET /api/project/transfer` | Bearer | 移交拥有者；`userId`；仅拥有者；原主变成员 |
| `POST /api/project/addDeputy` | Bearer | 任命管理员；仅拥有者 |
| `POST /api/project/deleteDeputy` | Bearer | 罢免管理员；仅拥有者 |
| `GET /api/project/exit` | Bearer | 退出；拥有者须先移交 |
| `GET /api/project/archived` | Bearer | 归档/取消归档；`projectId` · `type?=add|recovery`（默认 add）；仅拥有者；级联任务 `archived_follow` |
| `GET /api/project/remove` | Bearer | 软删项目；仅拥有者 |
| `GET /api/project/column/lists` | Bearer | 列列表；`projectId` |
| `GET /api/project/column/one` | Bearer | 列详情；`columnId`；须为项目成员 |
| `GET /api/project/column/add` | Bearer | 加列；须管理权限 |
| `GET /api/project/column/update` | Bearer | 改列；`columnId` · `name?` · `color?` · `sort?` |
| `GET /api/project/column/remove` | Bearer | 软删列；`columnId`；须管理权限；至少保留一列；级联软删列内任务（含子任务），不迁移 |
| `GET /api/project/flow/list` | Bearer | 工作流列表（含节点）；`projectId` |
| `POST /api/project/flow/save` | Bearer（管理） | 新建/更新；`projectId` · `id?` · `name?` · `items?`（JSON；空则默认 5 节点）；个人项目不可用；单流程 ≤10 节点 |
| `GET /api/project/flow/delete` | Bearer（管理） | 软删工作流及节点；`id` |
| `GET /api/project/tag/list` | Bearer | 项目标签列表；`projectId` |
| `POST /api/project/tag/save` | Bearer（管理） | 新建/更新；`projectId` · `id?` · `name`（≤20）· `color?`；同名拒绝 |
| `POST /api/project/tag/sort` | Bearer（管理） | 排序；`projectId` · `list`=[tagId,…] |
| `GET /api/project/tag/delete` | Bearer（管理） | 软删标签并清除任务关联；`id` |
| `GET /api/project/permission` | Bearer | 权限矩阵；无落库则返回默认；含 `points` |
| `GET /api/project/permission/update` | Bearer（管理） | 更新矩阵；`projectId` · `permissions`（JSON：`project_member`/`task_leader`/`task_assist` → 权限点数组）；个人项目不可用 |
| `POST /api/project/sort` | Bearer | 看板排序；`projectId` · `onlyColumn?` · `sort`（`[{id,task[]}]`）；换列时若列绑定工作流节点则同步 `flowItemId`/`flowItemName`（end 节点补写 `completeAt`，不回清空） |
| `POST /api/project/user/sort` | Bearer | 本人项目列表拖拽排序；`list`=[projectId,…] |
| `GET /api/project/top` | Bearer | 切换本人对该项目的置顶；返回 `{ id, topAt }` |
| `GET /api/project/log/lists` | Bearer | 项目/任务动态；`projectId` 与 `taskId` 二选一（`taskId` 优先）；`page?` · `pageSize?`（默认 20，最大 100）→ `{items,meta}`；项目视角排除 `taskOnly=1` |

默认列：未传 `columns` 时为「未完成 / 进行中 / 已完成」；传入时最多 30 列（对齐系统设置 `columnTemplate` 的客户端选型）。团队项目创建时自动建项目群（`group_type=project`），成员随 `project_users` 同步；个人项目 `dialogId=0`。

### `POST /api/project/sort`

| 字段 | 说明 |
| ---- | ---- |
| `projectId` | 项目 |
| `onlyColumn` / `only_column` | `1`/`true`：仅重排列顺序 |
| `sort` | JSON 数组：`[{ "id": columnId, "task": [taskId, ...] }, ...]`（query 字符串或 body） |

- `onlyColumn=1`：按数组顺序写列 `sort`
- 否则：按各列 `task` 顺序写未完成任务的 `columnId`+`sort`；子任务随主任务换列；已完成任务跳过；目标列若有工作流节点 `columnId` 绑定则联动更新 `flowItemId`/`flowItemName`（`status=end` 时补写完成时间）

列表顺序：`lists` 按本人 `topAt` 降序、再 `project_users.sort` 升序。`ProjectView.topAt` 为当前用户置顶时间。名称搜索仅匹配项目名（不含任务全文，全文走全局 search）。

### 会员参与项目 / 任务

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET /api/project/user/counts` | Bearer | `{project,todo,done}`；`userId` · `owner?` |
| `GET /api/project/user/tasks` | Bearer | 分页任务；`userId` · `owner?` · `projectId?` · `keys?` · `page?` · `pageSize?`；部门负责人只读 |
| `GET /api/project/user/projects` | Bearer | 分页项目；`userId` · `archived?` · `keys.name?` · `page?` · `pageSize?`；本人/管理员全量，部门负责人只读范围（`departmentReadonly`） |

权限见 [permissions.md](permissions.md)。任务接口见 [../task/api.md](../task/api.md)。
