# AI 助手 — 验收清单

## 已落地

- [x] 授权：`POST /api/assistant/auth` → Redis `streamKey`
- [x] 模型列表：`GET /api/assistant/models`（读 `aiBotSetting`）
- [x] 元素匹配：`POST /api/assistant/matchElements`（优先 embedding，回退词法；`strategy`）
- [x] 流式对话：`GET /api/ai/invoke/stream/{streamKey}`（进程内 OpenAI 兼容 SSE；`append`/`done`）
- [x] 操作派发：`operation/dispatch` → WS `operation`；`operation/result` + WS `operationResult`
- [x] 会话：`session/list|save|delete` + `bluedock_ai_assistant_sessions`
- [x] `session/save` · `newImages` 落盘（data URL / base64 / 已有 URL → `images` + 响应 `imageUrls`）
- [x] 反馈：`feedback/save` + `bluedock_ai_assistant_feedbacks`
- [x] 检索日志：`log/search` + `bluedock_ai_assistant_search_logs`
- [x] 管理端：`api/system/setting/aiBot*`（Key 掩码）

详见 [api.md](api.md) · [ai-assistant.md](../../infra/ai-assistant.md)。
