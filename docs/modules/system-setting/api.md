# 系统设置 — API

前缀 `api/system`。完整表见 [api-contract.md](../../contract/api-contract.md)。

## 已实现

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET\|POST /api/system/setting` | 管理员 | 通用设置（密码策略、注册、撤回时限、禁言、归档、`unclaimedTaskReminder`/`unclaimedTaskReminderTime`、`taskAiAutoAnalyze`…） |
| `GET\|POST /api/system/setting/file` | 管理员 | 上传上限、打包权限、图片/视频开关 |
| `GET\|POST /api/system/setting/oss` | 管理员 | 存储引擎（local/华为/阿里/腾讯/七牛）；见 [oss-settings.md](../../infra/oss-settings.md) |
| `GET /api/system/oss/check` | 管理员 | 存储连通性检测；见 [oss-settings.md](../../infra/oss-settings.md) |
| `GET\|POST\|DELETE /api/system/uploads` | 管理员 | 上传库列表/上传/软删；见 [upload-objects.md](../../infra/upload-objects.md) |
| `GET\|POST /api/system/setting/aiBot` | 管理员 | AI Key / 模型（`aiBotSetting`；密钥掩码同 OSS）；见 [ai-assistant.md](../../infra/ai-assistant.md) |
| `GET /api/system/setting/aiBotModels` | 管理员 | 当前已配 `models` 列表 |
| `GET /api/system/setting/aiBotDefaultModels` | 管理员 | 内置推荐模型 |
| `GET\|POST /api/system/setting/email` | 管理员 | SMTP 与邮件业务开关（密码掩码同 OSS）；见 [email.md](../../infra/email.md) |
| `GET /api/system/email/check` | 管理员 | 测试发信；`email=`；见 [email.md](../../infra/email.md) |
| `GET\|POST /api/system/setting/meeting` | 管理员 | Agora（证书/密钥掩码同 OSS）；见 [meeting-agora.md](../../infra/meeting-agora.md) |
| `GET\|POST /api/system/setting/appPush` | 管理员 | APP 推送（Key/Secret 掩码同 OSS）；见 [app-push.md](../../infra/app-push.md) |
| `GET\|POST /api/system/setting/thirdAccess` | 管理员 | LDAP |
| `GET /api/system/setting/thirdAccess/testLdap` | 管理员 | LDAP 探测 |
| `GET\|POST /api/system/setting/attendance` | 管理员 | 签到全局 |
| `POST /api/system/priority` | 登录；save 需管理员 | `type=get\|save`；`list:[{name,color,days,priority,isDefault}]`；存 `priority` |
| `POST /api/system/column/template` | 登录；save 需管理员 | `type=get\|save`；`list:[{name,columns}]`（columns 数组或逗号串）；存 `columnTemplate`；新建项目经 `project/add?columns=` 套用 |
| `GET /api/system/demo` | 匿名 | 演示帐号；须配置 `bluedock.demo.account`/`password`；→ `{account,password}`；未配置 `system.demo_disabled` |
| `GET /api/system/version` | 可匿名 | 产品名与版本；→ `{name:BlueDock,version,publish,deviceCount}` |
| `GET /api/system/get/info` | 可匿名 | 运行信息；→ `{name:BlueDock,version,java,time}` |
| `GET /api/system/get/updateLog` | 匿名 | 更新日志；`take?`（默认 50，10–100）；读 `CHANGELOG.md`（`bluedock.changelog.path`）；→ `{logVersion,updateLog}` |

`SYSTEM_SETTING=disabled`（或 `system.setting=disabled`）时**所有**设置写接口拒绝保存；通用设置 `get` 带 `writable`。

## 规划中 / 废弃 / 待实现

| URL | 说明 |
| --- | ---- |
| `setting/ai` | **废弃**：统一使用 `aiBot*`；勿再新增别名路由 |

`SYSTEM_SETTING=disabled` 时写拒绝且敏感字段掩码（演示打码），非独立「演示 API」。

存储：`bluedock_settings` 按 `name` 分项 JSON（含 `oss` · `emailSetting` · `fileSetting` · `aiBotSetting`）。**管理配置与上传进库总原则**见 [admin-db-settings.md](../../infra/admin-db-settings.md)。**上传库**见 [upload-objects.md](../../infra/upload-objects.md)。另：`POST /api/system/imageUpload` · `fileUpload`（写 `bluedock_upload_objects`）。详见 [overview.md](overview.md)。
