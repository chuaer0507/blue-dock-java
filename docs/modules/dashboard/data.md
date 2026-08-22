# 仪表盘 — 数据口径

无独立业务表。统计来自任务 / 项目成员关系。

| 卡片 | 口径（示意） |
| ---- | ------------ |
| 今日到期 | `end_at` 落在今日（用户时区）且未完成、未归档、可见 |
| 已超期 | `end_at` < now 且未完成 |
| 待完成 | 我负责或协助且未完成 |
| 协助 | `bluedock_users.owner=0` 且未完成 |
| 本周完成 | `complete_at` 落在本周 |

负责人视角：`api/dashboard/team/*`，范围=所选部门及下级成员参与的可见任务（受项目 `department_owner_view` 与任务 visibility 约束）。

可选 Redis 短缓存：`bluedock:dash:{userId}:{scope}` TTL 30–60s。
