# AI 助手 — API

前缀 `api/assistant`。鉴权：Bearer（登录用户）。

| URL | 方法 | 说明 | 状态 |
| --- | ---- | ---- | ---- |
| `auth` | POST | 签发短时 `streamKey`（Redis） | 已落地 |
| `models` | GET | 读 `aiBotSetting` 中 `*_model(s)` | 已落地 |
| `matchElements` | POST | 元素匹配 | 已落地（优先 embedding 余弦；失败回退词法；响应含 `strategy`） |

流式推理：`GET /api/ai/invoke/stream/{streamKey}`（SSE；见 [ai-assistant.md](../../infra/ai-assistant.md)）。
| `log/search` | POST | 知识库检索日志落库 | 已落地 |
| `feedback/save` | POST | like / dislike / 取消 | 已落地 |
| `operation/dispatch` | POST | 经 Kafka fanout 推 WS `operation` | 已落地 |
| `operation/result` | GET | 轮询 Redis 结果（取走即删） | 已落地 |
| `session/list` · `save` · `delete` | ANY | 会话 CRUD；`save` 支持 `newImages`（或兼容 `new_images`）落盘并返回 `imageUrls` | 已落地 |

### `session/save` 图片

| 字段 | 说明 |
| ---- | ---- |
| `newImages` | Map&lt;id, dataUrl\|base64\|url&gt;；或 List`[{id,content/data/url}]`；单次 ≤20，单张 ≤5MB |
| 响应 `imageUrls` | 本次新图 id → 公开 URL；合并写入会话 `images` JSON |

已有 `http(s)` / `media/` 路径直接入库；base64/`data:image/*` 经 `ObjectStorage` 写入 `media/assistant/{userId}/{yyyyMM}/…`。


WS：客户端回传 `{"type":"operationResult","data":{requestId,success,result,error}}`。

配置：`api/system/setting/aiBot*`（管理员；Key 掩码对齐 OSS/SMTP）。详见 [infra/ai-assistant.md](../../infra/ai-assistant.md)。
