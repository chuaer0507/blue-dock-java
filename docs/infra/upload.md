# 分片上传（实现）

产品说明见 [modules/upload/overview.md](../modules/upload/overview.md)。

**落地状态（P1）**：`bluedock-file` 已实现 `init` / `chunk` / `merge` / `cancel`；网盘秒传（同用户同 hash）；场景分流：

| scene | merge 落库 | 秒传 |
| ----- | ---------- | ---- |
| `file_cabinet`（默认） | `bluedock_files` | 是 |
| `project_task` | `bluedock_task_files`（经 `TaskAttachmentSink`） | 否；`init` 必带 `taskId` |

会话直传另走 `POST /api/dialog/message/sendFile`（`DialogChatFileSink` → `bluedock_files`，路径 `chat/{dialogId}/…`），不经分片会话。

配置：`bluedock.upload.base-dir` / `chunk-size` / `max-file-size-mb`；**物理引擎与公开域名**见管理端 [oss-settings.md](oss-settings.md)（`RuntimeObjectStorage`；merge/直传/imageUpload/fileUpload 已统一存储）。**配置与上传元数据必须进 MySQL**，总览见 [admin-db-settings.md](admin-db-settings.md)。

## 与系统设置 / 数据库

| 设置 / 表 | 作用 |
| --------- | ---- |
| `fileSetting.uploadMaxMb` 等 | 业务大小与打包/转码开关（`bluedock_settings`） |
| `oss` | local / 云厂商、allowExtensions、publicBaseUrl（`bluedock_settings`） |
| `bluedock_files` / `bluedock_task_files` | 网盘 / 任务附件元数据（merge / sendFile **已写库**） |
| `bluedock_upload_objects` | 系统 `imageUpload`/`fileUpload` 与管理上传库；见 [upload-objects.md](upload-objects.md) |

分片会话只在 Redis；**merge 成功后权威在 DB**，不得只留对象存储。

## 流程

```
POST /api/upload/init   → uploadId + 分片大小策略（或 done=true 秒传，仅网盘）
POST /api/upload/chunk  → 按 index 写入临时目录；Redis Set 记已收分片
POST /api/upload/merge  → 校验齐全 → 拼装 → 落盘 → 按 scene 写元数据
POST /api/upload/cancel → 删临时 + 清 Redis（会话缺失时静默成功）
```

### 请求参数（wire camelCase）

| 接口 | 主要参数 |
| ---- | -------- |
| init | `hash` · `size` · `name` · `scene` · `parentId` · `taskId`（`project_task` 必填） |
| chunk | `uploadId` · `index` · multipart `blob` |
| merge / cancel | `uploadId` |

### merge 响应

`UploadMergeView`：`scene` + 二选一载荷

| scene | 字段 | 说明 |
| ----- | ---- | ---- |
| `file_cabinet` | `file` | 网盘 `FileView` |
| `project_task` | `taskFile` | 任务附件元数据（同 `TaskFileView` 字段） |

## Redis

| Key（`RedisKeys`） | TTL | 内容 |
| ------------------ | --- | ---- |
| `bluedock:upload:{uploadId}` | 24h | userId、hash、size、name、scene、parentId、taskId、chunkSize、chunkCount |
| `bluedock:upload:{uploadId}:chunks` | 24h | 已收分片 index 集合 |

## 存储路径

- 临时：`{baseDir}/tmp/chunks/{userId}/{uploadId}/{index}.part`
- 正式：`{baseDir}/file/{type}/{yyyyMM}/{fileId}/content`

清理：cancel / merge 后删临时；`UploadTempCleanupScheduler` 每小时扫 `{baseDir}/tmp/chunks`，删除 Redis 会话已失效或 mtime>24h 的孤儿目录（`bluedock.upload.temp-cleanup-ms`，默认 3600000）。

## 限制

- 单文件大小：`bluedock.upload.max-file-size-mb`（默认 1024）
- 分片大小：`bluedock.upload.chunk-size`（默认 5MB）
- 同目录直接子项上限 300（仅网盘）
- 与 Nginx `client_max_body_size`、网关超时需对齐分片大小
