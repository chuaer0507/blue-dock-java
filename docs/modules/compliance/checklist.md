# 合规 — 验收清单

本模块**无独立合规 API / 配置面板**；验收为现有能力对照。

## 对照项（分散能力）

- [x] 内容审核：`complaint/lists|submit|action`（abuse-report）
- [x] 数据导出：任务 / 超期 / 签到 / 审批桥接（data-export）
- [x] 账号删除与离线：用户删除路径；插件 Hook 本仓不做 Docker 调度
- [x] 访问控制：项目 / 任务 / 系统权限 + LDAP
- [x] 传输加密：部署侧 HTTPS（Nginx）
- [x] 私有部署数据本地化：见 ops/deployment

## 明确不做

- [x] 无集中合规配置页 / 一键巡检 API（产品边界）
- [x] 无内置 DSR 工单 / cookie banner / 自动保留策略

详见 [overview.md](overview.md)。
