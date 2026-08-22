# 仪表盘 — 验收清单

## 后端（已落地）

- [x] 个人：今日到期、已超期、待完成、协助、本周完成（经 `user/tasks`·`user/counts`）
- [x] 负责人视角：部门切换、统计与任务列表（`dashboard/team/*` + `departmentId`）
- [x] 私密任务 / 关闭负责人视角的项目不泄露（visibility=1 · department_owner_view）

> 本仓只验收后端 API；UI 不在范围。

详见 [overview.md](overview.md) · [data.md](data.md) · [api.md](api.md)。
