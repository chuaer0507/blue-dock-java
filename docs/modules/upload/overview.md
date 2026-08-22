# 分片上传

> 功能说明。实现以 `docs/infra/upload.md`、`docs/contract/api-contract.md` 为准。

## 范围

大文件分片上传会话：初始化 → 分片 → 合并入库 / 取消。文件业务树与共享见 [file](../file/overview.md)。

### 触发策略（客户端约定）

| 条件 | 行为 |
| ---- | ---- |
| 文件 ≥ 10MB | 自动分片（单片约 5MB，多路上传） |
| 同用户同 hash 已存在 | 秒传（目标目录立刻出现记录） |
| 中断后续传 | 跳过已上传分片（本机 localStorage 索引 + 服务端会话） |

### 能力

- `init` / `chunk` / `merge` / `cancel`
- 场景：`file_cabinet`（网盘 / 秒传）· `project_task`（任务附件，`taskId` 必填）
- Redis 会话 key（示意 `upload:*`，TTL 24h）
- 临时分片目录超时清理（Worker / 定时任务）
- 受系统设置 `file_upload_limit` / `fileSetting.uploadMaxMb`（MB，空则兜底 1G）约束
- **物理存储引擎**（local/云）由管理端 `setting/oss` 配置（`bluedock_settings`），见 [oss-settings.md](../../infra/oss-settings.md)
- **配置与上传元数据进库**：见 [admin-db-settings.md](../../infra/admin-db-settings.md)

## API（前缀 `api/upload`）

| 路径 | HTTP | 说明 | 状态 |
| ---- | ---- | ---- | ---- |
| `api/upload/init` | POST | 启动上传会话 / 秒传 | 已落地 |
| `api/upload/chunk` | POST | 上传一个分片 | 已落地 |
| `api/upload/merge` | POST | 合并分片；按 scene 写网盘或任务附件 | 已落地 |
| `api/upload/cancel` | POST | 取消会话 | 已落地 |

实现细节见 [infra/upload.md](../../infra/upload.md)。

小文件亦可走 `api/system/fileUpload` / `imageUpload` 或消息侧 `dialog/message/sendFile`。

## 不支持

- 跨设备 / 跨浏览器可靠续传（客户端索引仅本机；服务端 hash 可兜底秒传）
- 移动端应用退后台保证续传
- 单文件夹直接子项过多时拒绝继续上传（产品约定上限，如 300）
- `project_task` 场景不做网盘秒传

## 相邻模块

- [file](../file/overview.md) — 入库后的树 / 版本 / 共享
- [task](../task/overview.md) — `project_task` 写入 `bluedock_task_files`
- [system-setting](../system-setting/overview.md) — `fileSetting` · `oss` · `emailSetting` · `aiBotSetting`（均落 `bluedock_settings`）
- [infra/upload.md](../../infra/upload.md) — 分片实现
- [infra/oss-settings.md](../../infra/oss-settings.md) — 存储引擎
- [infra/admin-db-settings.md](../../infra/admin-db-settings.md) — 上传进库 + 管理配置总对照
