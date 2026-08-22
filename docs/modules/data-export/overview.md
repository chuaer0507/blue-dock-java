# 数据导出

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **数据导出概览**

### 能力（怎么做）

- 管理员侧导出审批数据
- 导出签到数据
- 导出任务统计（按成员）
- 工作报告能不能批量导出
- 导出超期任务
- 用户列表能不能批量导出

## 核心概念

### 数据导出概览

## 定义
数据导出是BlueDock 内置的管理员功能，把系统数据按预设字段整理成 Excel 文件，通过系统机器人（`system-msg`）异步发到管理员的私聊。需要系统管理员才能触发。

## 支持的导出类型
后台「数据导出」菜单实际提供 4 类导出，分别对应不同的 API：

| 名称 | API | 说明 |
|---|---|---|
| 任务统计 | `api/project/task/export` | 按成员 + 时间段导出任务，含负责人/工时/状态 |
| 超期任务 | `api/project/task/exportOverdue` | 全系统未完成且已超期的任务 |
| 审批数据 | `api/approve/export` | 按流程分类 + 状态 + 时间段导出审批单 |
| 签到数据 | `api/system/attendance/export` | 按成员 + 日期段 + 时间段导出签到记录 |

## 关键属性
- **异步生成**：触发后立即返回；由 Kafka `bluedock.export.run` → `bluedock-worker-notify` 生成 **CSV**（UTF-8 BOM，可用 Excel 打开），完成后经 `bluedock.notify.send` 桌面通知，并经 `bluedock.export.notify` → boot/messenger 以 `system-msg` 机器人私聊投递下载文案。任务类下载 `/api/project/task/download?key=`；签到下载 `/api/system/attendance/download?key=`；审批下载 `/api/approve/download?key=`
- **下载链接限时**：Redis `bluedock:export:down:{key}` TTL **24h**；仅导出请求者可下载
- **机器人通知**：`system-msg@bot.system` 私聊（`ExportNotifyBridge` / `MessengerExportNotifyBridge`）；桌面通知通道并行保留
- **范围限制**：任务统计单次 ≤ **100** 成员、≤ **90** 天；签到导出 ≤ **100** 成员、≤ **35** 天且须开启签到；超期导出为全站未完成且 `end_at` 已过；审批导出 ≤ **90** 天且须 `processName`
- **已落地**：任务统计 / 超期 / 签到 / **审批导出桥接**（`ApproveExportBridge`，无插件拒受理）；报告与用户列表产品未开放

## 不支持
- 不支持导出工作报告、用户列表、项目列表（产品里未开放对应入口）
- 不支持自定义字段选择，导出列固定
- 不支持定时 / 周期性自动导出

## 入口
管理员侧入口和操作步骤见 `data-export.entry`。

## 不支持 / 边界

- 不支持导出原始 JSON / 数据库表，只导出预设字段的 Excel
- 不支持导出整个项目的所有任务（必须按成员筛选）
- 不支持把多人多份报告打包成一个 Excel
- 不支持普通成员自助导出，全部入口仅管理员可见
- 不支持自助导出含敏感字段（手机号、邮箱）的用户表
- 不支持选择字段，导出列固定
- 主程序后台没有「批量导出工作报告」入口
- 主程序后台没有「用户/成员批量导出 Excel」功能
- 单次导出最多 100 个成员，超出请分批
- 单次导出最多 35 天范围
- 单次最多 100 个成员
- 只导出"未完成"且 end_at 已过的任务；已完成的延期任务不在内
- 导出失败时机器人会推送错误信息，不会自动重试
- 导出文件不直接下载到浏览器，而是由系统机器人发到管理员的私聊
- 导出范围是全系统所有项目，不能按项目 / 成员筛选
- 必填成员 / 日期 / 时间三个参数，缺一不可
- 必填流程分类（procName），不能一次导全部分类
- 必须先在系统设置打开签到，否则报「此功能未开启」
- 日期范围最多 35 天
- 时间范围限制最大 90 天
- 没有 `api/report/export` 这样的接口
- 没有 `api/users/export` 这样的接口
- 没有计划截止时间（end_at 为空）的任务不会被纳入

## 相关文档

- 验收细项：[checklist.md](checklist.md) · [`CHECKLIST.md`](../CHECKLIST.md) → `data-export`
- 契约：`docs/contract/api-contract.md`（`project/task/export*` · `system/attendance/export*` · `approve/export*`）
- 异步：Kafka `bluedock.export.run`；完成通知 `bluedock.export.notify`（system-msg）+ `bluedock.notify.send`（desktop）；Redis `bluedock:export:down:{key}`；桥接 `ApproveExportBridge` · `ExportNotifyBridge`
