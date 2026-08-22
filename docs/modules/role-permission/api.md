# 角色与权限 — API 索引

权限校验下沉各领域 Service；本页只做**入口索引**，不重复矩阵全文。

## 系统级

| URL | 说明 | 文档 |
| --- | ---- | ---- |
| `GET /api/users/operation` | `setAdmin` / `clearAdmin` / `setTemporary` / `clearTemporary` / `disable`(+`handoverUserId`) / `enable` | [user-account/api.md](../user-account/api.md) |
| `POST /api/users/createUser` · `lists` · `import*` | 团队管理 | 同上 |
| `api/system/setting*` 等 | 系统设置读写 | [system-setting](../system-setting/overview.md) |

## 部门级

| URL | 说明 | 文档 |
| --- | ---- | ---- |
| `api/users/department*` | 部门 CRUD / 任命负责人与管理员 / sync | [org-department/api.md](../org-department/api.md) |
| `dashboard/team/stats\|tasks` | 负责人视角只读 | [dashboard/api.md](../dashboard/api.md) |

## 项目级

| URL | 说明 | 文档 |
| --- | ---- | ---- |
| `project/permission` · `permission/update` | 项目权限矩阵 | [project/permissions.md](../project/permissions.md) |
| `project/user*` · `invite*` · `transfer` 等 | 成员与角色 | [project/api.md](../project/api.md) |

## 任务级

| URL | 说明 | 文档 |
| --- | ---- | ---- |
| `project/task/*` 写路径 | 负责/协助/可见性校验 | [task/permissions.md](../task/permissions.md) |

## 规划 / 缺口

| 项 | 状态 |
| -- | ---- |
| `disable` 离职交接 | **已落地**：须 `handoverUserId`；项目/任务/部门归属迁移；完成后桌面 + system-msg 通知交接人 |
| 转让超级管理员（id=1） | **产品明确不做** |

响应与错误码走统一 `ResultModel` + `I18nKeys`。
