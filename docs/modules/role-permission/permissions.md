# 角色与权限 — 权限总览

四层互不自动继承：系统 → 部门 → 项目 → 任务。每次按**当前对象上的角色**判断。

## 层级速查

| 层 | 身份 | 能做什么 | 不能做什么 |
| -- | ---- | -------- | ---------- |
| 系统 | 超管 id=1 · admin identity | 团队管理、系统设置、LDAP、导出、任命部门角色 | 不自动获得任意项目/任务编辑权 |
| 部门 | 负责人 / 管理员 | 负责人视角只读、看部门仪表盘 | 不提升项目内写权限；不绕过私密任务 |
| 项目 | 负责人 / 管理员 / 成员 | 成员与列/工作流/矩阵（见项目文档） | 系统管理员身份不跨项目生效 |
| 任务 | 负责 / 协助 / 可见用户 | 内容与流转按矩阵 + 任务角色 | visibility 可挡住项目负责人 |

## 细项文档

| 主题 | 链接 |
| ---- | ---- |
| 项目角色与矩阵 | [project/permissions.md](../project/permissions.md) |
| 任务可见性 | [task/permissions.md](../task/permissions.md) |
| 部门任命 | [org-department/api.md](../org-department/api.md) |
| 系统 operation | [user-account/api.md](../user-account/api.md) |
| 账号边界 | [user-account/permissions.md](../user-account/permissions.md) |

## 边界（产品）

- **无**转让超级管理员接口
- **无**自定义新角色类型
- `disable` 须指定 `handoverUserId`，迁移项目/任务/部门归属（不自动转移 admin）
- 权限不足：统一业务错误码，禁止仅靠前端藏按钮
