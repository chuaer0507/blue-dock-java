# 角色与权限 — 验收清单

## 已落地

- [x] 系统管理员授予/取消：`users/operation` setAdmin/clearAdmin
- [x] 临时账号 / 启用停用：setTemporary/clearTemporary/disable/enable
- [x] 超管 id=1 保护（不可 clearAdmin / disable / 他人改资料）
- [x] 部门负责人 + 部门管理员任命；负责人视角 `dashboard/team/*`
- [x] 项目角色 owner/admin/member + 权限矩阵 `project/permission*`
- [x] 任务负责/协助/可见性校验（列表与写路径）
- [x] 离职交接：`disable` + `handoverUserId`（项目/任务/部门归属）
- [x] 交接完成通知：桌面 `CHANNEL_DESKTOP` + `system-msg` 私聊交接人

## 未落地

（无）

## 明确不做

- [x] 无转让超级管理员
- [x] 无自定义 RBAC 角色类型
- [x] 系统 admin 不自动成为项目/任务负责人

## 文档

- [x] overview 四级权限说明
- [x] api / data / permissions 索引收口
