# 对象存储 / 图片与文件上传（管理端可配）

系统设置（管理员）配置默认文件存储引擎（五引擎 + 热切换）；业务上传路径：`api/system/imageUpload` · `fileUpload` · `api/upload/*` · `dialog/message/sendFile*`。

配置与上传进库总原则见 [admin-db-settings.md](admin-db-settings.md)。

**实现状态**：**已落地**（`bluedock-common` RuntimeObjectStorage 五引擎；`GET|POST /api/system/setting/oss`；imageUpload/fileUpload 走统一存储；分片 merge / 会话直传已写业务表）。系统直传对象登记见 [admin-db-settings.md §6](admin-db-settings.md) · [upload-objects.md](upload-objects.md)（**已落地**）。

**关联**：[upload.md](upload.md) · [admin-db-settings.md](admin-db-settings.md) · [system-setting](../modules/system-setting/api.md) · [api-contract.md](../contract/api-contract.md)

---

## 1. 目标与范围

### 1.1 目标

- 管理员在「系统设置」配置**默认文件存储引擎**，保存后热切换运行时存储实现。
- 支持 **5** 家引擎：

| provider | 展示名 |
| -------- | ------ |
| `local` | 本地服务器存储 |
| `huawei` | 华为云 OBS |
| `aliyun` | 阿里云 OSS |
| `tencent` | 腾讯云 COS |
| `qiniu` | 七牛云 Kodo |

- **图片上传**与**文件上传**（含分片 merge 落盘、会话直传）统一走 `RuntimeObjectStorage`（命名可按模块调整），受同一套 `allowExtensions` / 公开 URL 规则约束。
- 现有 `fileSetting`（`uploadMaxMb`、打包权限、图片优化开关等）**继续生效**，与 OSS 文档正交：大小/业务开关 vs 引擎与桶。

### 1.2 v1 生效

| 能力 | v1 |
| ---- | -- |
| 全字段读写持久化 | ✅（建议 `bluedock_settings.name = oss` JSON，或独立表；与 `fileSetting` 分项） |
| `provider` 热切换 + put/delete | ✅ |
| `protocol` + `domain` → `publicBaseUrl` | ✅ |
| `allowExtensions` 校验（imageUpload / fileUpload / upload merge / 管理端上传） | ✅ |
| 本地 `storagePath`（空则沿用 `bluedock.upload.base-dir` / `./public`） | ✅ |
| 各云 endpoint/region/bucket/密钥；GET 密钥掩码 `********` | ✅ |
| `nameType=hash` 秒传 | ❌ 仅存配置，v1 不生效（网盘 hash 秒传仍按现 `upload/init` 逻辑） |
| `linkType` 含压缩 | ❌ 仅存配置；不调云图片处理 |

### 1.3 非目标（v1）

- 不按客户端拆分 OSS（仅系统管理员全局一套）。
- Alist / 又拍云不做。

---

## 2. 管理端 API

路径落在 system（与现设置风格一致）：

| Method | Path | 鉴权 | 说明 |
| ------ | ---- | ---- | ---- |
| GET | `/api/system/setting/oss` | 管理员 | 当前配置；密钥已设则 `********`；含合成 `publicBaseUrl` |
| POST | `/api/system/setting/oss` | 管理员 | 保存并热切换；密钥空或 `********` 保留原值；受 `SYSTEM_SETTING=disabled` 禁写 |
| GET | `/api/system/oss/check` | 管理员 | 连通性检测：对当前引擎 put 探针 `media/oss-check/{uuid}.txt` 后 delete → `{ok,provider,key,url}`；失败 `system.oss.check_failed` |

> 一接口一路径；若仅实现 POST 写，勿再注册 PUT 别名（或契约只保留一种动词）。

### 2.1 JSON 形态（camelCase）

```json
{
  "provider": "local",
  "nameType": "dateRandom",
  "linkType": "simple",
  "allowExtensions": "png,jpg,jpeg,gif,webp,zip,pdf,doc,docx,xls,xlsx,ppt,pptx,mp4,txt",
  "protocol": "https",
  "domain": "cdn.example.com",
  "publicBaseUrl": "https://cdn.example.com",
  "local": { "storagePath": "" },
  "huawei": { "endpoint": "", "accessKey": "", "secretKey": "", "bucket": "" },
  "aliyun": { "endpoint": "", "accessKeyId": "", "accessKeySecret": "", "bucket": "" },
  "tencent": { "region": "", "secretId": "", "secretKey": "", "bucket": "" },
  "qiniu": { "accessKey": "", "secretKey": "", "bucket": "", "region": "z0" }
}
```

枚举、默认值、`protocol`+`domain` ↔ `publicBaseUrl` 合成/拆分、保存校验见实现与错误 key 前缀 `system.oss.*` / `error.system.oss*`。

---

## 3. 业务上传入口（不变路径，统一存储）

| 入口 | 用途 | 存储约束 |
| ---- | ---- | -------- |
| `POST /api/system/imageUpload` | 图片（头像、富文本插图等） | `allowExtensions` ∩ 图片类；大小 ≤ `fileSetting.uploadMaxMb`（空=1G） |
| `POST /api/system/fileUpload` | 通用文件 | `allowExtensions`；同上大小 |
| `POST /api/upload/init\|chunk\|merge` | 分片（网盘 / 任务附件） | merge 落盘走 OSS；秒传规则见 [upload.md](../infra/upload.md) |
| `POST /api/dialog/message/sendFile` | 会话直传 | 同上 |
| 管理端上传库 | 列表/上传/删除 | 见 [upload-objects.md](upload-objects.md) |

公开 URL：`publicBaseUrl + "/" + objectKey`（v1 忽略压缩类 `linkType`）。

对象键建议分区（与现路径兼容迁移）：

| 场景 | key 前缀（示例） |
| ---- | ---------------- |
| 网盘文件 | `file/{type}/{yyyyMM}/{fileId}/…` |
| 任务附件 | `task/{taskId}/…` 或沿用现 `file/…` 并在元数据区分 |
| 会话 | `chat/{dialogId}/…` |
| 系统临时图 | `media/…` |

本地引擎：`storagePath` 空则使用 `bluedock.upload.base-dir`。

---

## 4. 与 `fileSetting` 的关系

| 配置项 | 来源 | 作用 |
| ------ | ---- | ---- |
| `uploadMaxMb` / 打包权限 / 图片优化 / 视频转码 | `fileSetting`（已有） | 业务策略 |
| `provider` / 桶 / 域名 / `allowExtensions` | `oss`（本文） | 物理落盘与公开访问 |

上传时两者都校验：先后缀与引擎，再大小与业务开关。

---

## 5. Redis / 配置缓存

| Key | 说明 |
| --- | ---- |
| `bluedock:setting:oss` | 可选缓存整份 OSS JSON；保存时主动失效 |

密钥禁止写入日志或非管理员可读接口。

---

## 6. 落地说明

| 组件 | 位置 |
| ---- | ---- |
| 运行时 | `com.bluedock.common.oss.*`（`RuntimeObjectStorage` + 五引擎） |
| 设置 | `OssSettingService` · `bluedock_settings.name=oss` · 启动热加载 |
| 上传 | `ChunkStorage.mergeToObject` · `DialogChatFileSinkImpl` · `SystemMediaUploadService` |
| yaml | `bluedock.oss.*`（可选）；local 空路径 → `./data/uploads` |

---

## 7. 验收清单

- [x] GET/保存 OSS；密钥掩码；`SYSTEM_SETTING=disabled` 禁写
- [x] 云无 `domain` 拒绝保存
- [x] 后缀不在 `allowExtensions` → 明确错误
- [x] 切换 provider 后新上传走新引擎；旧 URL 仍可按原 publicBaseUrl 访问（不强制搬迁）
- [x] `fileSetting.uploadMaxMb` 与 OSS 同时生效
- [x] 分片 merge / 会话直传 / imageUpload / fileUpload 四条路径均经统一存储
- [x] `GET /api/system/oss/check` put 探针 + delete；失败 i18n

---

## 8. 变更记录

| 日期 | 说明 |
| ---- | ---- |
| 2026-08-04 | 初稿：挂 system/setting/oss；业务路径保持既有前缀 |
| 2026-08-04 | 代码落地：五引擎 + setting/oss + 四条上传路径 |
| 2026-08-05 | 连通性检测 `GET /api/system/oss/check` |
