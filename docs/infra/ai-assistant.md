# AI 助手与模型 Key（管理端可配 · 数据库）

对齐系统设置里的 **文件 / OSS / SMTP** 管理方式：管理员配置供应商 Key、可见模型与开关，**写入 `bluedock_settings.name=aiBotSetting`**；助手 / 任务 AI / 运行时只在服务端读原文。**不**按客户端形态拆配置、不以打包内 Key 为权威源。总览见 [admin-db-settings.md](admin-db-settings.md)。

**实现状态**：**设置 API 已落地**；助手桥接 API 已落地；**流式对话已进程内落地**（`GET /api/ai/invoke/stream/{streamKey}`）；**`matchElements` 优先 embedding**（`embeddingModel`，默认 `text-embedding-3-small`；失败回退词法）。

**关联**：[assistant/overview](../modules/assistant/overview.md) · [assistant/api](../modules/assistant/api.md) · [system-setting/api](../modules/system-setting/api.md) · [email.md](email.md) · [oss-settings.md](oss-settings.md) · [admin-db-settings.md](admin-db-settings.md) · [api-contract.md](../contract/api-contract.md)

---

## 1. 目标与范围

### 1.1 目标

- 管理员配置 **AI 供应商 API Key**、**baseUrl**、**可见模型列表**、总开关等。
- 与 `fileSetting` / `oss` / `emailSetting` 相同模式：`bluedock_settings` 分项 JSON、仅系统管理员读写、`SYSTEM_SETTING=disabled` 禁写。
- GET **掩码**密钥字段（已设则为 `********`）；POST 空、`********` 或含 `****` 的回显串时 **保留原密文**。
- 普通登录用户经 `GET /api/assistant/models` **只拿到** `*Model` / `*Models`，**不下发任何 Key**。

### 1.2 已生效

| 能力 | 状态 |
| ---- | ---- |
| `aiBotSetting` 全字段读写 | ✅ |
| GET Key 掩码 / POST 保留密文（`apiKey` · `openaiKey` · `*Secret`） | ✅ |
| `aiBotModels` / `aiBotDefaultModels` | ✅（管理端列表；def 为内置推荐） |
| `assistant/models` 过滤非模型字段 | ✅ |
| `assistant/auth` · session · feedback · operation | ✅（含 `session/save` `newImages`） |
| 真实模型流式对话 | ✅ `GET /api/ai/invoke/stream/{streamKey}`（SSE `append`/`done`；读 `aiBotSetting`） |
| `matchElements` embedding | ✅ 优先 `/v1/embeddings` 余弦；失败/未开回退词法；响应 `strategy` |
| 报告 AI 整理（`report/aiGenerate`） | ✅ OpenAI 兼容 `AiBotChatService` + `SystemReportAiDraftBridge`（须 `aiBotSetting.open` + Key） |
| 任务 AI 外部模型 | ✅ 优先 `AiBotChatService`；失败/未开回退启发式 + 卡片 |
| 会话语音转写（`convertRecord` / `voiceToText`） | ✅ `AiBotChatService.transcribe` → `/v1/audio/transcriptions` |

### 1.3 非目标

- 不按客户端拆分 Key（全局一套）。
- 不在管理 GET 响应里回传明文 Key（含 `aiGatewayKey`；运行时仅服务端读库）。
- 群内 @ 机器人绑定的模型与本设置可独立演进（产品边界见 system-setting overview）。

---

## 2. 管理端 API

与上传 / 邮件设置同挂 `api/system`：

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/system/setting/aiBot` | 管理员 | 当前配置；密钥字段已设则 `********` |
| POST | `/api/system/setting/aiBot` | 管理员 | 保存；密钥空/掩码保留原值；受 `SYSTEM_SETTING=disabled` 禁写 |
| GET | `/api/system/setting/aiBotModels` | 管理员 | 配置内 `models` 列表 |
| GET | `/api/system/setting/aiBotDefaultModels` | 管理员 | 内置推荐模型（如 gpt-4o-mini / deepseek-chat） |

> 旧路径 `api/system/setting/ai` 已废弃（契约保留占位说明）；一接口一路径，勿再加 PUT 别名。

### 2.1 JSON 形态（全 camelCase）

存 `bluedock_settings.name = aiBotSetting`。统一字段与多供应商字段均为 camelCase：

```json
{
  "open": "close",
  "provider": "openai",
  "apiKey": "********",
  "baseUrl": "",
  "model": "gpt-4o-mini",
  "models": [{ "id": "gpt-4o-mini", "name": "GPT-4o mini", "provider": "openai" }],
  "systemPrompt": "",
  "embeddingModel": "text-embedding-3-small",
  "openaiKey": "********",
  "openaiModels": ["gpt-4o-mini"],
  "claudeKey": "********",
  "claudeModels": [],
  "deepseekKey": "********",
  "deepseekModels": ["deepseek-chat"],
  "aiGatewayKey": "********"
}
```

| 字段 | 说明 |
| ---- | ---- |
| `open` | `open`/`close`：AI 能力总开关（产品侧） |
| `provider` / `apiKey` / `baseUrl` / `model` | 统一默认供应商 |
| `models` | 管理端结构化模型列表 |
| `systemPrompt` | 可选系统提示 |
| `embeddingModel` | `matchElements` 用；默认 `text-embedding-3-small` |
| `{provider}Key` | 各供应商 API Key（如 `openaiKey`）；**密钥** |
| `{provider}Models` / `{provider}Model` | 对该供应商暴露给客户端的模型 id 列表 |
| `aiGatewayKey` | 官方网关 token（若使用）；GET 掩码，仅服务端读原文 |

**密钥判定**：字段名以 `Key`/`Secret` 结尾。`openaiModels` 等非密钥。读库时会把旧 snake（`openai_key` 等）迁到 camel。

实现：`AiBotSettingService`（`bluedock-system`）；助手侧读库：`AssistantService.models()`。

---

## 3. 与上传 / 邮件配置的对照

| 维度 | 上传 / OSS | 邮件 SMTP | AI Key（aiBot） |
| ---- | ---------- | --------- | ---------------- |
| 管理员入口 | `setting/file` · `setting/oss` | `setting/email` | `setting/aiBot*` |
| 存储 | `fileSetting` · `oss` | `emailSetting` | `aiBotSetting` |
| 密钥策略 | GET `********`；POST 空/掩码保留 | 同左 | 同左（多 Key 字段） |
| 禁写 | `SYSTEM_SETTING=disabled` | 同左 | 同左 |
| 对普通用户 | 上传走业务 API | 收信 | 仅 `assistant/models` 的 `*Model(s)` |

---

## 4. 职责拆分（运行时）

| 层 | 职责 |
| -- | ---- |
| `bluedock-assistant` | 鉴权码、会话元数据、操作派发桥、反馈/检索日志；`models` 过滤；**流式入口** `AiInvokeController` |
| `AiBotChatService`（bluedock-system） | OpenAI 兼容同步/流式 chat；读 `aiBotSetting` 原文 Key |
| 管理配置 | 本节 `setting/aiBot*` |

### 流式协议

1. 客户端 `POST /api/assistant/auth` 得 `streamKey`（Redis TTL 10min，绑定 userId/context/model）
2. `GET /api/ai/invoke/stream/{streamKey}`（**无需 Bearer**；key 一次性消费）
3. SSE 事件：`append` → `data: {"content":"..."}`；结束 `done` → `{}` 或 `{"error":"..."}`

反向代理须关闭对该路径的响应缓冲（Nginx `proxy_buffering off`）。

### 安全

- 授权码短时有效、与 userId 绑定（`bluedock:assistant:stream:{streamKey}`）
- 操作派发校验 WS 会话归属或当前用户在线集合
- **API Key 仅服务端保存**；管理 GET 掩码；客户端 `models` 无 Key

### 观测

- 流式错误见应用日志；知识库检索命中日志（`api/assistant/log/search`）
- 用户反馈（`feedback/save`）

### 任务 AI

`task/ai_*`：事件表 + 采纳/忽略 + **优先 OpenAI 兼容模型**（`AiBotChatService`）+ 失败回退启发式 + 任务群 Markdown 卡片已落地。

### 报告 AI

`POST /api/report/aiGenerate`：`SystemReportAiDraftBridge` → `AiBotChatService`（须 `aiBotSetting.open` + Key/`baseUrl`/`model`）；关闭或失败 → `report.ai_unavailable` / `report.ai_failed`。
