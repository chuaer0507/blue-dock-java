# 合规

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **合规能力概览**

### 能力（怎么做）

- 合规配置项检查清单

## 核心概念

### 合规能力概览

## 定义
主程序没有把「合规」做成单独菜单，但通过多个分散功能覆盖了数据合规、用户隐私、内容审核等场景。这里把和合规相关的现有能力索引到一起，方便管理员对照内部合规要求逐项检查。

## 涉及的现有能力
| 维度 | 现有手段 | 主程序入口 |
|---|---|---|
| 内容审核 | 用户举报 + 管理员处理 | `abuse-report.concept` |
| 数据导出（数据可携性） | 任务 / 审批 / 签到 Excel 导出 | `data-export.concept` |
| 账号下线 / 数据删除 | 团队管理 → 删除成员；应用侧生命周期仅见 install/update/uninstall Hook（**无** `user_offboard`） | 团队管理 |
| 审计 | 操作日志（部分模块）+ 审批历史 | 模块自带 |
| 访问控制 | 项目 / 任务 / 系统三级权限 + LDAP | `role-permission.permission-denied` |
| 数据本地化 | 全私有部署 + Docker，所有数据库在自有服务器 | 部署阶段 |
| 加密传输 | HTTPS（需自配 Nginx 证书） | 部署阶段 |

## 删除请求（GDPR 第 17 条）
主程序删除成员后，**本仓不内置**向插件派发 `user_offboard` 的 Hook（与旧产品文档不同）。应用生命周期回调仅覆盖 `install|update|uninstall`（见 [appstore/api.md](../appstore/api.md)）。用户数据清理依赖管理员流程与各侧车自行约定。

## 不支持
- 没有内置的合规配置面板
- 没有自动数据保留策略（你需要外部脚本定期清理）
- 没有内置数据出口审计（数据导出动作未单独留审计日志）
- 不内置 cookie 同意弹窗、隐私政策签署、DSR 工单等模块

## 相关
- 合规配置项细节：`compliance.howto`
- 入口与责任人：`compliance.entry`

## 不支持 / 边界

- BlueDock 主程序没有专门的「合规设置」集中页面
- BlueDock 没有「一键合规检查」按钮
- 不内置 GDPR DSR 工单系统，需要管理员人工响应
- 不支持自动数据保留周期清理（需手动或脚本）
- 合规问题的法律责任在私有部署方，BlueDock 仅提供能力
- 多数合规项是人工 + 脚本组合，没有自动巡检
- 没有自动获取用户同意（cookie banner 等）的开关

## 相关文档

- 验收细项：[checklist.md](checklist.md) · [`CHECKLIST.md`](../CHECKLIST.md) → `compliance`
- 关联：`abuse-report` · `data-export` · `ldap` · 部署 HTTPS
