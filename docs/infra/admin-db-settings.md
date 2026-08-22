# 管理端配置与上传落库

本文约定：BlueDock 中 **上传文件元数据必须进数据库**；**上传引擎 / SMTP / AI 模型 Key** 等运维配置由管理员写入 **MySQL**，不依赖各端本地私配。

**实现状态（摘要）**：

| 能力 | BlueDock |
| ---- | --------- |
| 配置落库 | `bluedock_settings`（按 `name` 分项 JSON） |
| 上传对象登记 | `bluedock_upload_objects` + `api/system/uploads*`；`imageUpload`/`fileUpload` 写库 **已落地** |
| OSS / 存储引擎 | `GET/POST /api/system/setting/oss`；连通性 `GET /api/system/oss/check`（[oss-settings.md](oss-settings.md)）**已落地** |
| 业务上传上限等 | `GET/POST /api/system/setting/file`（`fileSetting`）**已落地** |
| SMTP | `GET/POST /api/system/setting/email`（[email.md](email.md)）**已落地** |
| AI 模型 Key | `GET/POST /api/system/setting/aiBot`（[ai-assistant.md](ai-assistant.md)）**已落地** |

**关联**：[upload.md](upload.md) · [oss-settings.md](oss-settings.md) · [email.md](email.md) · [ai-assistant.md](ai-assistant.md) · [system-setting/api](../modules/system-setting/api.md) · [database.md](../data/database.md) · [api-contract.md](../contract/api-contract.md)

---

## 1. 铁律

1. **配置进库**：OSS、文件策略、SMTP、AI Key、会议、APP 推送等管理员项一律存 `bluedock_settings`；YAML / env 仅作空库回退或本地开发默认，**不以各端私有配置为权威源**。
2. **密钥掩码**：GET 已设密钥返回 `********`；POST 空或 `********`（或含 `****` 回显）保留原密文。
3. **禁写开关**：`SYSTEM_SETTING=disabled` 时拒绝所有设置写接口。
4. **上传进库**：对象落盘 / 上云后，必须有 **MySQL 登记**（网盘 → `bluedock_files`；系统素材 / 管理上传库 → `bluedock_upload_objects`）。禁止「只写对象存储、库中无行」作为正式路径。
5. **路径兼容**：业务 REST 走既有前缀（`/api/system/*` · `/api/upload/*` · `/api/file/*`）。

---

## 2. 配置与上传对象

### 2.1 配置表（`bluedock_settings`）

| `name` | 说明 |
| ------ | ---- |
| `oss` | 五引擎 + allowExtensions + publicBaseUrl |
| `fileSetting` | 上传上限 MB、打包、转码等业务开关 |
| `emailSetting` | SMTP + 邮件业务开关 |
| `aiBotSetting` | AI 供应商 Key / 可见模型 |
| `meetingSetting` · `appPushSetting` · … | 会议 / 推送等 |

Kafka 走部署配置；业务通知 Topic 常量在 `KafkaTopics`，不强制进 `bluedock_settings`。

### 2.2 上传对象表

`bluedock_upload_objects` 要点：

| 列 | 含义 |
| -- | ---- |
| `object_key` | 存储键（唯一） |
| `url` | 公开 URL |
| `category` | `releases` / `media` / `files` / `other` |
| `provider` | 当时使用的存储引擎 |
| `uploader_id` 等 | 上传者；软删 `deleted_at` |

场景映射：

| 场景 | 表 | 状态 |
| ---- | -- | ---- |
| 用户网盘 / 会话文件 | `bluedock_files`（+ `path` / `hash`） | ✅ 已落地 |
| 任务附件 | `bluedock_task_files` | ✅ 已落地 |
| 系统 `imageUpload` / `fileUpload`、管理端素材库 | **`bluedock_upload_objects`** | ✅ 已落地 |
| 分片临时态 | Redis `bluedock:upload:*`（非权威元数据） | ✅；merge 后必须写业务表或 upload_objects |

管理端列表/删除 API：见专文 **[upload-objects.md](upload-objects.md)**（`api/system/uploads*`）。

---

## 3. 上传配置（OSS + fileSetting）

两层正交：物理引擎与业务限制分开配置。

| 层 | `bluedock_settings.name` | API | 职责 |
| -- | -------------------- | --- | ---- |
| 物理引擎 | `oss` | `GET\|POST /api/system/setting/oss`；`GET /api/system/oss/check` | provider、密钥、域名、allowExtensions、热切换 `RuntimeObjectStorage`；连通性 put/delete 探针 |
| 业务策略 | `fileSetting` | `GET\|POST /api/system/setting/file` | `uploadMaxMb`、打包权限、图片优化、视频转码 |

字段与交互细则见 [oss-settings.md](oss-settings.md)（错误 key 前缀 `system.oss.*`）。

上传入口（均须受上述配置约束，且最终有 DB 行）：

| 入口 | 落库目标 |
| ---- | -------- |
| `POST /api/upload/init\|chunk\|merge` | `bluedock_files` 或 `bluedock_task_files` |
| `POST /api/dialog/message/sendFile*` | `bluedock_files` |
| `POST /api/system/imageUpload` · `fileUpload` | 写 `bluedock_upload_objects`（已落地） |
| 管理端上传库 `api/system/uploads*` | `bluedock_upload_objects`（已落地） |

---

## 4. AI 模型 Key 配置（数据库）

- 存：`bluedock_settings.name = aiBotSetting`
- API：`GET|POST /api/system/setting/aiBot`（及 `aiBotModels` / `aiBotDefaultModels`）
- 规则：与 OSS 相同的密钥掩码 / 保留；普通用户 `GET /api/assistant/models` **只回** `*_model(s)`，不下发 Key
- 全文：[ai-assistant.md](ai-assistant.md)

AI Key 与其它运维项同属 `bluedock_settings` 分项，**不**写进客户端包或仅 env。

---

## 5. SMTP 邮箱配置（数据库）

- 存：`bluedock_settings.name = emailSetting`
- API：`GET|POST /api/system/setting/email`；测试 `GET /api/system/email/check`
- 规则：`smtpPassword` GET 掩码；POST 空/`********` 保留；Worker 读库发信
- 全文：[email.md](email.md)

与上传配置相同：**管理员可配、落库、热读**；演示环境可用 `SYSTEM_SETTING=disabled` 禁写。

---

## 6. 缺口与落地顺序

配置类与上传库 **代码已落地**。`imageView` 已对接本表（本人 media）。后续可选：`releases` 分类启用、缩略图独立生成。

---

## 7. 文档索引

| 文档 | 内容 |
| ---- | ---- |
| 本文 | 总则 + 铁律 |
| [upload-objects.md](upload-objects.md) | **上传库可管理**（列表/上传/删除） |
| [oss-settings.md](oss-settings.md) | 存储引擎 |
| [upload.md](upload.md) | 分片实现与设置关系 |
| [email.md](email.md) | SMTP |
| [ai-assistant.md](ai-assistant.md) | AI Key |
| [meeting-agora.md](meeting-agora.md) / [app-push.md](app-push.md) | 会议 / APP 推送（同类 DB 配置） |
