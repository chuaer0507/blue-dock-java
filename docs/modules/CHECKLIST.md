# 功能模块写作清单（细项）

每个模块落地前勾选。状态写在本文件或对应 `modules/<id>/checklist.md`。

图例：`[ ]` 待写 · `[~]` 起草 · `[x]` 定稿

---

## B1 P0

### dashboard — 仪表盘
- [x] overview（个人视角 / 部门负责人视角）
- [x] api（team/stats、team/tasks；今日/超期/待办聚合规则）— [dashboard/api.md](dashboard/api.md)
- [x] data（统计口径）— [dashboard/data.md](dashboard/data.md)
- [x] permissions（负责人范围：`user/tasks`·`user/counts` 本人/管理员/部门树只读；`dashboard/team/*` 须可管理）
- [x] checklist：后端聚合已落地 — [dashboard/checklist.md](dashboard/checklist.md)
- [x] 已落地：`dashboard/team/stats|tasks`（部门树）；`project/user/counts|tasks`

### calendar — 日历
- [x] overview（数据源=任务时间，非独立实体）
- [x] api / data（start_at/end_at；无独立表）— [calendar/api.md](calendar/api.md) · [calendar/data.md](calendar/data.md)
- [x] checklist：后端 `task/calendar` + update 改期；无 iCal — [calendar/checklist.md](calendar/checklist.md)

### messenger — 即时通讯
- [x] overview（会话类型：私聊/群/任务/项目/机器人）
- [x] api（会话与消息全量接口）— [messenger/api.md](messenger/api.md)
- [x] data（dialog / msg / unread / todo）— [messenger/data.md](messenger/data.md)
- [x] permissions（群主/管理员/禁言）— [messenger/permissions.md](messenger/permissions.md)
- [x] checklist：建群、成员、转让、解散、退出、文本/图/文件、任务卡片、引用、撤回、投票、接龙、待办、置顶、免打扰、已读、搜索、历史、@ 提及、`group/searchUser`、`common/list`、`okr/*`
- [x] 已落地：dialog 主路径 + 普通群管理 + 机器人单聊禁对发 / 禁入群
- [x] 契约收口见本文件「契约未实现收口」· messenger（废弃项保留占位）

### file — 文件
- [x] overview（个人树 / 共享 / 版本）
- [x] api（文件 CRUD + Office token）— [file/api.md](file/api.md)
- [x] data（树、分享、历史版本）— [file/data.md](file/data.md)
- [x] checklist：上传、新建、重命名、移动、删除、共享、公开链接、预览、搜索、回收站恢复 — [file/checklist.md](file/checklist.md)

### project — 项目
- [x] overview（个人 vs 团队；角色 owner/admin/member）
- [x] api / data / permissions（项目权限点）— [project/api.md](project/api.md) · [project/permissions.md](project/permissions.md)
- [x] checklist：创建/编辑、成员、邀请、移交、退出、归档删除、列 CRUD、工作流、排序置顶、导出、搜索 — [project/checklist.md](project/checklist.md)

### task — 任务
- [x] overview（主任务/子任务/模板/循环）
- [x] api / data / permissions（负责人/协助/可见）— [task/api.md](task/api.md) · [task/permissions.md](task/permissions.md) · [task/recurring.md](task/recurring.md)
- [x] checklist：快建/全量建/@#创建、编辑、完成、换列、跨项目移动、排序、子任务、归档删除恢复、附件、复制、模板、关联、优先级/标签/颜色/可见性、AI 建议、**循环完成生成** — [task/checklist.md](task/checklist.md)
- [x] 已落地：CRUD、完成、子任务、归档删除、附件、dialog、calendar、`move`、`upgrade`、`copy`、`related*`、`easyLists`、`template_*`、可见性、`column/remove`、`export*`、`ai_*`、`resetFromLog`、快建 + `sendTaskId`、`loop`/`loopAt`；WS `bluedock.*`

---

## B2

### meeting — 会议
- [x] open/link/tourist/invitation + 会议卡片 + 自动关房（见 infra/meeting-agora）
- [x] checklist：后端主路径已落地（token / 链接 / 卡片 / 关房）— [meeting/checklist.md](meeting/checklist.md)

### report — 工作报告
- [x] overview（日报/周报）— [report/overview.md](report/overview.md)
- [x] api：主路径 + share / analysisSave + **template 任务汇总** + **aiGenerate（OpenAI 兼容）**
- [x] checklist：模板任务汇总；分享；AI 整理草稿（`SystemReportAiDraftBridge`）

### attendance — 签到（P1）
- [x] overview / api / data（手动 + WiFi + 定位 + 人脸桥接骨架 + 管理设置 + 导出 + 提醒 + 法定节假日 + 请假过滤桥接；识别算法由 face 插件）
- [x] checklist：人脸表 / FaceBridge / 登记与刷脸 API（识别算法由 face 插件实现）


---

## B3

### application / apps / micro-app / bot
- [x] application：菜单与排序 API（microAppMenu / appSort）— [application/checklist.md](application/checklist.md)
- [x] apps：系统应用 + 管理员应用入口映射（文档定稿）— [apps/checklist.md](apps/checklist.md)
- [x] micro-app：自定义菜单 / 角标 / 个人排序；安装注册表骨架（无 Docker）
- [x] bot：自建 CRUD + 系统种子 + Webhook(message / memberJoin / memberLeave / dialogOpen)

### notify
- [x] email-notice：SMTP 设置 API（密码掩码）+ Worker 投递 + 测试发送 + 未读汇总调度（免打扰→silence）
- [x] push-notice：APP 推送设置 API + Worker customizedcast；PC 在线 **10s 延时 + 已读跳过**；`bluedock_app_push_logs`；Alias 已落地
- [x] desktop-notify / mobile-notify：客户端开关+权限；移动时段静音本地；后端 alias + 会话消息 push + mute/silence — [notify/checklist.md](notify/checklist.md)

---

## B4

### user-account
- [x] 登录/注册/验证码/扫码、密码、邮箱验证、设备、导入、注销、搜索（主路径已落地；overview 起草可继续润色）
- [x] **已落地**：登录 RSA + 失败阈值验证码 — [auth-wire.md](user-account/auth-wire.md)
- [x] **已落地**：`editPassword`（RSA oldPassword/password+keyId；LDAP 回写；system 禁改）
- [x] **已落地**：`lists` / `createUser` / `operation`（管理员；License 扩容守卫；setAdmin/clearAdmin/setTemporary/clearTemporary/disable/enable）
- [x] **已落地**：`search` / `search/ai`（基础字段；disable/bot/项目过滤；page 或 take）
- [x] **已落地**：`import/template` · `import/preview` · `import`（CSV / xls / xlsx；RSA 确认导入；≤500）
- [x] **已落地**：`login/qrCode`（create/confirm/status；code≥32；TTL 30s；一次性消费）
- [x] **已落地**：`register/needInvite`；`email/send|edit|verification`（表 + regVerify 登录守卫；SMTP 可选）

### user-settings
- [x] overview / api / data / checklist — [user-settings/](user-settings/)
- [x] 已落地：`editData` · `editPassword` · `email/*` · `appSort*` · `delete/account` · `device/*`
- [x] 个性标签 `users/tags/*`（表 + CRUD + recognize）
- [x] `api/privacy` HTML（匿名；`static/privacy.html`）

### role-permission
- [x] overview / api / data / permissions / checklist — [role-permission/](role-permission/)
- [x] 四级权限已散落落地（system operation · 部门 · 项目矩阵 · 任务可见性）
- [x] 离职交接：`disable` + `handoverUserId` 迁移项目/任务/部门归属
- [x] 明确不做：转让超管
- [x] 交接完成通知：桌面 + system-msg 私聊交接人

### system-setting
- [x] 通用 / 文件 / aiBot（Key 掩码）/ email / meeting（证书掩码）/ appPush（Key 掩码）/ LDAP / 签到（管理员读写；`SYSTEM_SETTING=disabled` 禁写）
- [x] 任务优先级 `POST /api/system/priority`、列模板 `POST /api/system/column/template`
- [x] 新建项目 `project/add?columns=` 套用列模板列名（最多 30）
- [x] **已落地**：存储引擎 `setting/oss` + 连通性 `oss/check` — [oss-settings.md](../infra/oss-settings.md)
- [x] **文档定稿**：上传进库 + OSS/SMTP/AI Key 管理配置落库 — [admin-db-settings.md](../infra/admin-db-settings.md)
- [x] **已落地**：上传库可管理 — [upload-objects.md](../infra/upload-objects.md)
- [x] **已落地**：`GET /api/system/imageView` 对接 `bluedock_upload_objects`（本人 media）
- [x] 旧 `setting/ai`：**废弃**，统一 `aiBot*`；演示打码 = `SYSTEM_SETTING=disabled` 掩码行为（非独立 API）

### license / ldap / data-export / abuse-report / compliance / appstore
- [x] license：离线录入 + status 校验 + 扩容守卫；在线 **local + remote HTTP**（端点见 [infra/license.md](../infra/license.md)）
- [x] ldap：配置、登录时同步（**无定时全量按钮**）、`ldapSyncLocal` 反向写入；**改密回写已落地**
- [x] data-export：任务/超期/签到/审批桥接已落地；完成通知桌面 + `system-msg` 私聊；报告/用户产品明确不做 — [data-export/checklist.md](data-export/checklist.md)
- [x] abuse-report：`complaint/lists|submit|action`（桌面通知管理员；无自动封禁）
- [x] compliance：分散能力对照清单已定稿（无集中配置 API）— [compliance/checklist.md](compliance/checklist.md)
- [x] appstore：catalog + install/update/uninstall 注册表闭环（联动 microAppMenu；可选 HTTP lifecycle Hook；无本进程 Docker）

---

## 横切（非独立业务模块但必写）

### search / assistant / upload
- [x] search：联系人/项目/任务/文件/消息；`docs`/`opensearch`/`mysql` 可切换；全量重建 API 已落地
- [x] assistant：授权、模型、元素匹配、操作派发、会话（含 `newImages`）、反馈、知识库检索日志（桥接层）— [assistant/checklist.md](assistant/checklist.md)
- [x] upload：init / chunk / merge / cancel（含 `project_task` → `bluedock_task_files`）；临时盘定时清理；配置落库见 [admin-db-settings.md](../infra/admin-db-settings.md)；系统直传写 `bluedock_upload_objects` + 管理 `/uploads` + `imageView`
- [x] 客户端支撑接口：`version` / `prefetch` / `get/ip|chinaIp|info` / `device/*` / `socket/status` / `key/client` / `appPush/alias` / `presence`（契约见 api-contract）

### 同步类（实现注意）
- [x] LDAP：按需登录同步，不是定时全量
- [x] 部门成员同步：子部门 → 父部门单向合并（跳过禁用/机器人；返回 `skippedDisabledCount`）
- [x] 搜索索引：增量 + 全量重建（`api/search/rebuild`）；引擎 mysql/docs/opensearch 可切换
- [x] 实时同步：WebSocket 事件保证任务/消息即时同步（messenger + task.* + column.* + project.sort）

---

## 能力缺口对照

REST `/api/*` 主路径与实现缺口 P0–P2 **已齐**；开放项（插件 / 发版回归）见 **[api-contract.md「能力缺口（parity）」](../contract/api-contract.md#能力缺口parity)** 与 [docs/README.md §4](../README.md#4-未完成清单)（勿在本表重复抄路径）。

## 契约未实现收口

契约已列、代码尚未落地的接口。状态：`[ ]` 待实现 · `[x]` 已实现 · `废弃` / `明确不做` 见各模块 api。实现后须同步改本表与模块 checklist。

### messenger

| 路径 | 状态 |
| ---- | ---- |
| `dialog/telephone` | [x] 已落地 |
| `dialog/message/latest` · `detail` · `download` · `mergeDetail` | [x] 已落地 |
| `dialog/message/dot` · `checked` · `stream` | [x] 已落地 |
| `dialog/message/mark` · `tag` · `color` · `translation` | [x] 已落地 |
| `dialog/message/sendNotice` · `sendAnon` · `sendBot` | [x] 已落地 |
| `dialog/message/sendTemplate` · `sendApprove` | [x] 已落地 |
| `dialog/message/sendRecord` · `convertRecord` · `voiceToText` | [x] 已落地 |
| `dialog/message/sendAiAssistant` · `sendLocation` | [x] 已落地 |
| `dialog/message/aiGenerate` · `webhookMessageToAi` · `applied` | 废弃（占位 `{deprecated:true}`） |
| `dialog/sticker/search` · `dialog/message/sendSticker` | [x] 已落地 |

### users / project / system

| 路径 | 状态 |
| ---- | ---- |
| `users/share/list` | [x] 已落地 |
| `users/annual/report` | [x] 已落地 |
| `project/user/projects` | [x] 已落地 |
| `system/demo` | [x] 已落地 |
| `system/get/updateLog` | [x] 已落地 |
| `system/setting/ai` | 废弃（用 `aiBot*`） |
| `users/import` · `import/preview` · `import/template` | [x] 已落地 |

---

## API 资源归属（contract 落表时用）

| 资源前缀 | 归属模块 |
| -------- | -------- |
| `users/*` | user-account / org-department / attendance / bot / favorite / meeting |
| `project/*` | project / task |
| `dialog/*` | messenger |
| `file/*` | file |
| `upload/*` | upload |
| `report/*` | report |
| `system/*` | system-setting / infra |
| `dashboard/*` | dashboard |
| `license/*` | license |
| `assistant/*` | assistant |
| `search/*` | search |
| `apps/*` | micro-app（角标等） |
| `complaint/*` | abuse-report |
| `public/*` | attendance 等公开接口 |
