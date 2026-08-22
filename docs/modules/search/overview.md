# 全局搜索

> 功能说明。实现以 `docs/contract/api-contract.md`、`docs/architecture/search-index.md` 为准。

## 范围

跨实体统一检索入口，结果仅返回当前账号有权访问的对象。

### 能力

- 搜联系人 / 项目 / 任务 / 文件 / 历史消息
- 桌面快捷键：`Cmd/Ctrl+F` 或 `Cmd/Ctrl+/`
- 移动端：消息 / 项目 / 通讯录页内搜索栏（按场景拆分）
- 未配置全文引擎时降级为 MySQL `LIKE`（慢且弱）

### 不在本模块

- 会话内局部搜消息：见 [messenger](../messenger/overview.md)
- 项目列表筛选：见 [project](../project/overview.md)
- 文件树内搜索：见 [file](../file/overview.md)

## 入口

| 端 | 入口 |
| -- | ---- |
| 桌面 | 全局搜索框（快捷键 / 顶栏图标） |
| 移动 | 各 Tab 顶部搜索栏（能力子集） |

## API（前缀 `api/search`）

| 路径 | 说明 |
| ---- | ---- |
| `api/search/contact` | 联系人 |
| `api/search/project` | 项目 |
| `api/search/task` | 任务 |
| `api/search/file` | 文件 |
| `api/search/message` | 消息 |
| `api/search/rebuild` | 管理员全量重建 |
| `api/search/rebuild/status` | 重建进度 |

## 索引与降级

| 状态 | 行为 |
| ---- | ---- |
| 全文引擎可用 | 语义 / 全文检索（实现见 search-index） |
| 引擎不可用 | MySQL `LIKE` 降级 |
| 引擎可用但未回填 | 新数据可搜、历史可能搜不到 → 需重建索引 |

增量 / 全量同步走 Kafka `bluedock.search.index`，由 `bluedock-worker-index` 消费。详见 [search-index.md](../../architecture/search-index.md)。

## 不支持

- 自定义同义词词典
- 字段级权限过滤（只到对象可见性）
- 部分微应用业务数据默认不进索引

## 相邻模块

- [assistant](../assistant/overview.md) — 助手内可触发检索
- [upload](../upload/overview.md) / [file](../file/overview.md) — 文件入索引时机
