# AI 助手

> 功能说明。实现以 `docs/contract/api-contract.md`、`docs/infra/ai-assistant.md` 为准。

## 范围

浮窗式智能助手：对话、模型选用、页面元素匹配、操作派发、知识库检索日志与反馈。会话按当前账号私有。

### 前置

- 管理员已配置至少一个可用模型（供应商 / API Key / Base URL）
- 能力可由独立 AI 服务承载；主程序提供鉴权与操作桥接 API

### 能力

- 打开浮窗对话、多会话管理（创建 / 列表 / 打开 / 重命名 / 删除）
- 获取可见模型列表
- 元素向量匹配（页面上下文）
- 派发页面操作并回取结果
- 记录帮助知识库检索日志、保存回复反馈
- 项目 / 任务等场景的 AI 生成入口（字段旁小按钮，走项目域 API）

### 入口

| 端 | 路径 |
| -- | ---- |
| 桌面 | 右上「+」→ AI 助手；右下悬浮按钮；快捷键 `Cmd/Ctrl+I` |
| 移动 | 侧边浮动按钮（可收起） |
| 内嵌 | 项目名 / 描述等字段旁 AI 按钮 |

无独立左侧一级菜单。

## API（前缀 `api/assistant`）

接口清单与落地状态见 [api.md](api.md)。

相关：`api/system/setting/aiBot*`（管理员模型 / API Key 配置，见 [infra/ai-assistant.md](../../infra/ai-assistant.md)）、`api/dialog/session/*`（对话侧 AI 会话）、`api/project/task/ai_*`（任务建议：生成/采纳/忽略已落地启发式骨架）。

## 不支持

- 主程序不内置模型权重，必须配置外部供应商
- 普通成员不可改模型供应商配置
- 无自动「连通性测试」——保存后在对话中试用

## 相邻模块

- [system-setting](../system-setting/overview.md) — AI / 机器人相关设置
- [messenger](../messenger/overview.md) — 以助手身份发消息
- [report](../report/overview.md) — AI 整理汇报
- [search](../search/overview.md) — 助手触发检索

## 相关文档

- 验收细项：[checklist.md](checklist.md)
- API：[api.md](api.md)
- 基础设施：[ai-assistant.md](../../infra/ai-assistant.md)
