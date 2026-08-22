# 搜索 — API

前缀 `api/search`。

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `contact` | 联系人（昵称/邮箱） | 已落地（源表 LIKE） |
| `project` | 我参与的项目 | 已落地 |
| `task` | 我可见项目下主任务 | 已落地 |
| `file` | 我的文件 | 已落地 |
| `message` | 我会话中的文本消息 | 已落地 |

| `POST rebuild` | 全量重建索引（限管理员；`types` 可选） | 已落地 |
| `GET rebuild/status` | 重建进度（限管理员） | 已落地 |

引擎：`bluedock.search.engine` = `mysql` \| `docs`（默认）\| `opensearch`，失败自动降级。详见 [search-index.md](../../architecture/search-index.md)。
