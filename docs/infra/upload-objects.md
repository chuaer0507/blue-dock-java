# 上传库（可管理）

管理端「上传库」：对象落盘/上云后 **登记到 MySQL**，支持列表、按分类筛选、删除（软删 + 删存储对象）。API：`GET/POST/DELETE /api/system/uploads`；表：`bluedock_upload_objects`。

**实现状态**：**已落地**（`bluedock_upload_objects`；`GET|POST|DELETE /api/system/uploads`；`imageUpload`/`fileUpload` 写库）。网盘 / 任务附件仍走 `bluedock_files` / `bluedock_task_files`（不属本库）。

**关联**：[admin-db-settings.md](admin-db-settings.md) · [oss-settings.md](oss-settings.md) · [upload.md](upload.md) · [database.md](../data/database.md) · [system-setting/api](../modules/system-setting/api.md)

---

## 1. 目标与范围

### 1.1 目标

- 系统素材（头像、富文本插图、通用附件）与管理端主动上传的文件 **可查、可删、可追溯**。
- `object_key` 唯一、`category` 分流、`provider` 记录当时引擎、软删。
- 受当前 `oss` + `fileSetting` 约束（`allowExtensions`、大小上限）；存储走 `RuntimeObjectStorage`。

### 1.2 与业务网盘的边界

| 能力 | 表 | API 前缀 | 说明 |
| ---- | -- | -------- | ---- |
| 用户网盘树 / 共享 / 秒传 | `bluedock_files` | `api/file/*` · `api/upload/*`（file_cabinet） | 已落地；**不**进上传库 |
| 任务附件 | `bluedock_task_files` | `api/upload/*`（project_task）· 任务附件接口 | 已落地 |
| 会话直传文件 | `bluedock_files`（chat 路径） | `dialog/message/sendFile*`（含 `sendFiles` 群发） | 已落地 |
| **上传库（本文）** | `bluedock_upload_objects` | `api/system/uploads*` + 直传写库 | ✅ 已落地 |

发版产物（`category=releases`）：无对等发版产物库时可暂不启用 `releases`；预留枚举值即可。

### 1.3 非目标（v1）

- 不按客户端拆分上传库（全局一套，仅系统管理员管理）。
- 不做跨租户 `store_id`（本仓无商家多租户则省略或恒空）。
- 不替代网盘回收站语义；上传库删除 = 软删登记 + 尽量删对象。

---

## 2. API 一览

| 项 | 约定 |
| -- | ---- |
| 表 | `bluedock_upload_objects` |
| 列表 | `GET /api/system/uploads` |
| 上传 | `POST /api/system/uploads` |
| 删除 | `DELETE /api/system/uploads`（`id=`）或 `…/uploads/{id}` **择一**，契约只保留一种 |
| 鉴权 | 系统管理员（`AdminGuard` / identity 含 `admin`） |
| 直传 | 现有 `imageUpload` / `fileUpload` **合并写本表**（category=`media`/`files`） |
| 视图字段 | `UploadObjectView` camelCase（见 §4.2） |

> 一接口一路径：禁止同时注册 `DELETE /uploads/{id}` 与 `DELETE /uploads?id=` 别名。

---

## 3. 数据模型（`bluedock_upload_objects`）

物理表前缀 `bluedock_`；时间 UTC `DATETIME(3)`；软删 `deleted_at`。

| 列 | 类型（示意） | 说明 |
| -- | ------------ | ---- |
| `id` | BIGINT PK | `IdGenerator` |
| `object_key` | VARCHAR(512) UNIQUE | 存储键，如 `media/202608/…` · `files/…` |
| `url` | VARCHAR(2048) | 公开 URL（`publicBaseUrl` + key） |
| `category` | VARCHAR(32) | `media` \| `files` \| `other`（预留 `releases`） |
| `original_name` | VARCHAR(255) | 原始文件名 |
| `content_type` | VARCHAR(128) | MIME |
| `size_bytes` | BIGINT | 字节 |
| `provider` | VARCHAR(16) | 上传时 `oss.provider`（`local`/云） |
| `uploader_id` | BIGINT NULL | 上传者 `bluedock_users.id` |
| `created_at` | DATETIME(3) | |
| `deleted_at` | DATETIME(3) NULL | 非空=已删 |

索引建议：`uk_object_key`；`(category, created_at)`；`(uploader_id, created_at)`。

开发期（版本 ≤1.0.0-SNAPSHOT）：可并入既有 Flyway V1 或按 [database.md](../../.agents/rules/database.md) 约定改脚本；**生产后**仅 additive `V{n+1}`。

---

## 4. 管理端 API

前缀 `api/system`；列表/上传/删除需 **系统管理员**。`SYSTEM_SETTING=disabled` 时禁写（POST/DELETE）；GET 仍可读。

| Method | Path | 说明 |
| ------ | ---- | ---- |
| GET | `/api/system/uploads` | 分页列表 |
| POST | `/api/system/uploads` | multipart 上传并登记 |
| DELETE | `/api/system/uploads` | 软删；query `id` |

直传（登录即可）：`POST /api/system/imageUpload`（media）· `fileUpload`（files）— 同样 INSERT 本表；响应含 `id`/`url`/`path` 等。

### 4.1 查询参数（GET）

| 参数 | 说明 |
| ---- | ---- |
| `category` | 可选：`media` / `files` / `other` |
| `q` | 可选：匹配 `original_name` / `object_key` 模糊 |
| `page` | 默认 1 |
| `pageSize` | 默认 20，最大 100（兼容 `pageSize`） |

响应：

```json
{
  "list": [ /* UploadObjectView */ ],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

### 4.2 `UploadObjectView`（camelCase）

| 字段 | 说明 |
| ---- | ---- |
| `id` | |
| `objectKey` | |
| `url` | |
| `category` | |
| `originalName` | |
| `contentType` | |
| `sizeBytes` | |
| `provider` | |
| `uploaderId` | |
| `createdAt` | |

无 `storeId` 或恒 `null`。

### 4.3 上传（POST）

- multipart：`file`（必填）· `category`（默认 `files`；`media` 限图片后缀）
- 校验：`oss.allowExtensions`、`fileSetting.uploadMaxMb`
- key 前缀：`media/` 或 `files/`（与现 `SystemMediaUploadService` 一致）
- 事务顺序建议：put 对象 → INSERT 行；失败补偿删对象
- 响应：`UploadObjectView`

### 4.4 删除（DELETE）

- 按 `id` 查未删行 → 软删 → `ObjectStorage.delete(objectKey)`（对象已不存在可忽略）
- 响应：`{ "ok": true }`

### 4.5 与现有直传的关系

| 现有接口 | 改造后 |
| -------- | ------ |
| `POST /api/system/imageUpload` | put + **INSERT** `category=media`；响应可保留 `{url,path,name,size,ext}` 并增加 `id` |
| `POST /api/system/fileUpload` | 同上，`category=files` |
| `GET /api/system/imageView` | 本人 `media` 列表；wire `{dirs,files}`（`dirs` 恒 `[]`；见 §4.6） |
| `POST /api/system/uploads` | 管理端显式上传库入口（列表页用）；逻辑与上复用同一 Service |

普通登录用户可继续用 imageUpload/fileUpload（产品若要求仅管理员写库，则直传也 `requireAdmin`——**默认同现：登录即可上传，但行必进库**；列表/删除仅管理员）。

### 4.6 imageView（图片空间）

| 项 | 约定 |
| -- | ---- |
| 路径 | `GET /api/system/imageView`（契约唯一路径；不设 `get/imageView` 等别名） |
| 权限 | 登录用户；仅看本人 `uploader_id` + `category=media` |
| 查询 | `path` 可选：消毒后作 `object_key` 前缀（如 `media/202608`）；无本地目录树 |
| 上限 | 最近 200 条 |
| 响应 | `{ "dirs": [], "files": [ { type, title, path, url, thumbnail, inode, id } ] }` |
| `dirs` | 恒空（对象存储扁平 key，不模拟文件夹） |
| `files[].path` | `objectKey` |
| `files[].thumbnail` | 同 `url`（未单独生成缩略图） |
| `files[].inode` | `createdAt` UTC epoch 秒 |
| `files[].id` | 上传库主键（增量字段，向前兼容） |

---

## 5. 权限与安全

- 管理列表 / 删除：仅系统管理员。
- `imageView`：本人 media；禁止看他人上传。
- 直传写库：记录 `uploader_id`；禁止匿名。
- 密钥与引擎：只读当前 `OssSettingService`；不在上传响应回传 OSS 密钥。
- 删除不可物理抹掉审计需求时：仅软删行、对象删除失败打日志（对象已不存在仍软删，可接受）。

错误（i18n key 示意，实现时进 `I18nKeys`）：

| 场景 | key 示意 |
| ---- | -------- |
| 空文件 | `system.upload.empty`（已有） |
| 后缀不允许 | `system.oss.ext_not_allowed` |
| 过大 | `upload.too_large` |
| 记录不存在 | `system.upload.not_found` |
| 非管理员 | `admin.required` |

---

## 6. 落地顺序

1. ~~文档 / 契约~~
2. ~~Flyway：`bluedock_upload_objects`（并入 V1）~~
3. ~~`UploadObjectRepository` + `UploadObjectService`~~
4. ~~`SystemMediaUploadService` 经 Service 写库~~
5. ~~`/uploads` GET/POST/DELETE~~
6. ~~单测~~
7. ~~`GET /api/system/imageView` 对接本表~~

---

## 7. 文档索引

| 文档 | 内容 |
| ---- | ---- |
| 本文 | 上传库可管理设计 |
| [admin-db-settings.md](admin-db-settings.md) | 配置落库 + 上传进库总铁律 |
| [oss-settings.md](oss-settings.md) | 引擎与 allowExtensions |
| [upload.md](upload.md) | 分片（网盘/任务，非本库） |
