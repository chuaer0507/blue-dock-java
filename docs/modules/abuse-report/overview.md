# 举报

> 功能说明（从产品能力清单同步）。实现以 `docs/contract/api-contract.md` 与后端代码为准。

## 范围

### 概念

- **举报管理是什么**

### 能力（怎么做）

- 处理一条举报

## 核心概念

### 举报管理是什么

## 定义
举报管理（complaint）是 BlueDock 内置的违规内容处理后台。任何成员在群聊 / 个人对话遇到违规消息时可提交举报，系统管理员在后台审核并标记「已处理」或删除。模型对应 `App\Models\Complaint`，控制器 `ComplaintController`。

## 关键属性
- **举报对象**：对话（`dialog_id`），不能对单条消息单独举报
- **举报人**：`userId`，提交后系统机器人会通知前 10 个在线管理员
- **举报类型**：固定 7 种（id 10/20/30/40/50/60/70），见下
- **状态**：0 待处理 / 1 已处理 / 2 已删除
- **附件**：最多 N 张图片（API `images[]`，落库 `bluedock_complaints.images` JSON，含 path）
- **原因**：必填文本

## 举报类型受控词表
| id | 含义 |
|---|---|
| 10 | 诈骗诱导转账 |
| 20 | 引流下载其他 APP 付费 |
| 30 | 敲诈勒索 |
| 40 | 照片与本人不一致 |
| 50 | 色情低俗 |
| 60 | 频繁广告骚扰 |
| 70 | 其他问题 |

## 通知机制
举报提交后，后端取 `identity LIKE '%,admin,%'` 且按 `online_at` 倒序的前 10 位管理员，由 `system-msg` 系统机器人 template 消息推送「收到新的举报信息：{原因}」。

## 不支持
- 不支持对单条消息举报（只能对整个 dialog）
- 不支持举报者匿名（后端会存 `userId`）
- 不支持自定义举报类型
- 不支持自动黑名单 / 封禁

## 相关
- 处理流程：`abuse-report.handle`
- 入口：`abuse-report.entry`

## 不支持 / 边界

- 「删除」是数据库硬删除（`delete()`），不可恢复
- 「已处理」状态不可逆，无法重置为「待处理」
- 不支持自动 AI 内容审核（仅人工处理）
- 举报后不会自动封号或屏蔽，需要管理员手动决定
- 处理动作不会自动通知举报人或被举报对话成员
- 普通成员只能提交举报，看不到「举报管理」后台

## 相关文档

- API：[api.md](api.md) · [api-contract.md](../../contract/api-contract.md)
- 数据：`bluedock_complaints`（见 [database.md](../../data/database.md)）
- 验收细项：[`CHECKLIST.md`](../CHECKLIST.md) → `abuse-report`
