# 数据导出 — 验收清单

## 已落地（管理员）

- [x] 任务统计：`POST/GET /api/project/task/export` + `download?key=`
- [x] 超期任务：`/api/project/task/exportOverdue`
- [x] 签到：`/api/system/attendance/export` + `download`
- [x] 审批：`/api/approve/export` + `download`（`ApproveExportBridge`；无插件拒受理）
- [x] 异步：Kafka `bluedock.export.run` → Worker CSV → Redis 下载 key（24h）→ 桌面通知 + `system-msg` 私聊（`bluedock.export.notify`）

## 明确不做（产品未开放）

- [x] 无 `api/report/export`（工作报告批量导出）
- [x] 无 `api/users/export`（用户列表批量导出）
- [x] 无自定义列 / 定时导出

详见 [overview.md](overview.md)。
