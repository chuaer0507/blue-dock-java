# 系统应用 / 管理员应用 — 验收清单

入口多为**前端路由**映射到已有后端模块；本清单定文档边界。

## 系统应用（示例 → 后端）

- [x] 签到 → attendance
- [x] 工作报告 → report
- [x] 收藏 / 最近 → favorite
- [x] 机器人 → bot
- [x] 会议 → meeting
- [x] 审批 → approve 插件（导出桥接已有）

## 管理员应用（示例 → 后端）

管理员分区卡片固定 6 个（LDAP / 邮件通知 / APP 推送 / 举报管理 / 数据导出 / 团队管理）：

- [x] LDAP / 邮件 / APP 推送 → system-setting（及 infra）
- [x] 数据导出 → data-export
- [x] 举报 → abuse-report
- [x] 团队管理 → user-account / org-department

签到规则、会议参数：在常用区对应抽屉的「设置」入口（仅管理员可见），后端仍走 `system/setting/attendance`、`system/setting/meeting`，**不是**独立管理员应用卡片。

## 明确

- [x] 不按 desktop/web 拆后端模块；应用中心不另开 Maven 域

详见 [overview.md](overview.md)。
