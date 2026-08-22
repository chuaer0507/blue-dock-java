# 工作报告 — API

前缀 `api/report`。

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `my` | 我发送的 | 已落地 |
| `receive` | 我接收的（status=unread/read） | 已落地 |
| `store` | 保存并发送（新建/编辑） | 已落地 |
| `template` | 标题 / sign / **任务汇总正文**（负责人任务） | 已落地 |
| `aiGenerate` | AI 整理草稿（须已有 content；`SystemReportAiDraftBridge` / OpenAI 兼容） | 已落地 |
| `detail` | 详情（`id` 或分享短码 `code`）；含本人 `aiAnalysis` | 已落地 |
| `mark` / `read` | 标记已读 | 已落地 |
| `unread` | 未读数 | 已落地 |
| `lastSubmitter` | 上次接收人 | 已落地 |
| `share` | 分享到会话（短码 + Markdown 消息） | 已落地 |
| `analysisSave` | 保存当前用户 AI 解读 | 已落地 |

### template

- 日报：已完成工作 + 今日未完成
- 周报：已完成 + 本周未完成 + 下周拟定计划
- 仅汇总当前用户 **owner=1** 任务；周报 `offset` 按周（`plusWeeks`）

### share

| 参数 | 说明 |
| ---- | ---- |
| `id` / `ids` | 报告 id，逗号分隔，≤20 |
| `dialogId` / `dialogIds` | 目标会话，≤20；消息总数 ≤20 |
| `refresh` | `yes` 强制换新短码 |

返回：`list[{id,code,url}]`、`messageIds`、`sharedCount`。表 `bluedock_report_links`。

### aiGenerate（POST）

Body：`type`、`content`（非空）。须管理员开启 `aiBotSetting` 并配置 Key；否则 `report.ai_unavailable`。调用失败 → `report.ai_failed`。

### analysisSave（POST）

Body：`id`（或 `reportId`）、`text`（或 `analysisText`）、可选 `model` / `meta`。按 `(report_id, user_id)` upsert。

表：`bluedock_reports` · `bluedock_report_receives` · `bluedock_report_links` · `bluedock_report_ai_analyses`（V1）。完整契约见 [api-contract.md](../../contract/api-contract.md)。
