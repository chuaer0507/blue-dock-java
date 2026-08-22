# 搜索索引

全文 / 语义检索的数据同步方案。产品能力见 [modules/search/overview.md](../modules/search/overview.md)。

## 目标

| 对象 | 索引字段（示意） | 删除策略 |
| ---- | ---------------- | -------- |
| 联系人 | 昵称、邮箱、拼音 | 用户禁用 / 删除 |
| 项目 | 名称、描述 | 归档可配置是否可搜；删除移除 |
| 任务 | 标题、描述、编号 | 删除 / 不可见则按权限过滤 |
| 文件 | 名、路径、扩展名 | 删除移除 |
| 消息 | 文本内容 | 撤回可删索引或标记 |

结果层**必须**再做权限过滤（任务可见性、项目成员、文件共享）。

## 同步路径

```
写库成功 → Outbox / 直接 produce
         → topic bluedock.search.index
         → bluedock-worker-index upsert / delete
              ├─ bluedock_search_docs（始终）
              └─ OpenSearch（可选双写）
```

| 模式 | 时机 |
| ---- | ---- |
| 增量 | 创建 / 更新 / 删除事件 |
| 全量重建 | 管理员 `POST api/search/rebuild` → Kafka `action=rebuild` → worker 扫源表回填 |

### 全量重建 API

| 路径 | 说明 |
| ---- | ---- |
| `POST api/search/rebuild?types=` | 管理员；`types` 省略或 `all` = contact,project,task,file,message；逗号分隔子集 |
| `GET api/search/rebuild/status` | 管理员；`queued` / `running` / `done` / `failed` / `idle` |

互斥锁：`bluedock:search:rebuild:lock`（2h）；进度：`bluedock:search:rebuild:status`（24h）。

## 检索引擎（`bluedock.search.engine`）

| 值 | 行为 |
| -- | ---- |
| `mysql` | 源表 `LIKE`（权限在 SQL） |
| `docs`（默认） | 读 `bluedock_search_docs` + 权限 JOIN；空结果 / 失败降级 `mysql`；联系人仍走源表 |
| `opensearch` | HTTP 查 OpenSearch；不可用或空结果降级 `docs` → `mysql` |

配置：

```yaml
bluedock.search.engine: docs
bluedock.search.opensearch.enabled: false
bluedock.search.opensearch.url: http://127.0.0.1:19200
bluedock.search.opensearch.index: bluedock-search
```

本地 / 联调可选 Compose profile：

```bash
# 开发依赖栈（宿主机应用 → localhost:19200）
docker compose -f deploy/docker-compose.dev.yml --profile opensearch up -d

# 联调镜像栈（容器内 → http://opensearch:9200）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.dev --profile opensearch up -d

# 然后
# BLUEDOCK_SEARCH_OPENSEARCH_ENABLED=true BLUEDOCK_SEARCH_ENGINE=opensearch
```

生产 OpenSearch 外置，不写进 `docker-compose.prod.yml`。

## 降级

引擎不可用 → `FallbackSearchEngine`：opensearch → docs → mysql，并打 warn 日志。

## 落地状态（P3）

| 项 | 状态 |
| -- | ---- |
| `api/search/*` MySQL LIKE 门面 | 已落地 |
| Topic `bluedock.search.index` + `SearchIndexPublisher` | 已落地 |
| `bluedock-worker-index` → `bluedock_search_docs` upsert/delete | 已落地 |
| messenger / task / project 发布索引事件 | 已接入 |
| `docs` 引擎 + 降级门面 | **已落地** |
| OpenSearch 可选双写 + 检索 | **已落地**（Compose profile；默认关闭） |
| 全量重建 API | **已落地**（`api/search/rebuild` + worker 扫表） |
| 联系人 / 文件索引写入 | 重建已覆盖；文件增量事件仍可按业务补 |
