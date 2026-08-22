# API 契约总表

> 路径形态见 [api-routing.md](api-routing.md)。实现保持**路径与语义向前兼容**；HTTP 动词可逐步规范化（读 GET、写 POST），勿随意改路径。  
> 响应信封：`{ code, message, data }`，字段 camelCase 全词（见 [naming.md](naming.md)）。  
> **实现进度**：P0–P5 业务 REST 主路径基本齐（见 [ops/migration.md](../ops/migration.md)）；能力缺口见下文「能力缺口（parity）」。细则见各模块 `docs/modules/*/api.md`。

## users

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/users/login | get/post | 登录；RSA `password`+`keyId`（[+验证码]）；POST 推荐 JSON body，见 modules/user-account/auth-wire.md |
| api/users/login/qrCode | get | 扫码登录（type=create\|confirm\|status；confirm 需 Bearer；status 成功发新 token） |
| api/users/login/needCode | get | 是否需要验证码 `{ need }` |
| api/users/login/codeImage | get | 验证码图片流（兼容） |
| api/users/login/codeJson | get | 验证码 JSON `{ key, imageBase64 }`（推荐） |
| api/users/logout | get | 退出登录 |
| api/users/token/expire | get | 查询当前 access token 剩余有效期 `{ ttlSeconds, expireAt }`（expireAt=UTC 毫秒） |
| api/users/token/refresh | get/post | 无感续期：`refreshToken` → `{ token, refreshToken }`；失败 `code=-2` |
| api/users/register/needInvite | get | 是否需要邀请码 `{ need }`（reg=invite） |
| api/users/register | post | 自助注册：RSA `password`+`keyId`+`emailCode`[+`invite`]；成功可带 `token`/`refreshToken` |
| api/users/email/code | get | 邮箱 OTP：`email`+`type`=reg\|reset；无 SMTP 时 `devCode` |
| api/users/password/reset | post | 忘记密码：`email`+`emailCode`+RSA `password`+`keyId` |
| api/users/info | get | 获取我的信息 |
| api/users/info/managedDepartments | get | 获取我可切换负责人视角的部门列表 |
| api/users/info/departments | get | 获取我的部门列表 |
| api/users/editData | get | 修改自己的资料 |
| api/users/editPassword | get | 修改自己的密码（RSA `oldPassword`+`password`+`keyId`；LDAP 回写） |
| api/users/search | get | 搜索会员（`key`/`disable`/`isBot`/`projectId`/`noProjectId`/`nameAz`；`page` 或 `take`） |
| api/users/search/ai | get | 获取 AI 系统机器人（ai-*@bot.system） |
| api/users/basic | get | 获取指定会员基础信息 |
| api/users/extra | get | 获取会员扩展信息（`userId` 缺省自己；含 nameAz/emailVerify/isBot） |
| api/users/lists | get | 会员列表（管理员；`key`/`page`/`pageSize`/`isBot`；默认不含机器人） |
| api/users/operation | get | 操作会员（管理员；type=setAdmin\|clearAdmin\|setTemporary\|clearTemporary\|disable\|enable；disable 须 handoverUserId） |
| api/users/createUser | post | 创建用户（管理员；RSA `password`+`keyId`） |
| api/users/import/preview | post | 批量导入预览（管理员；CSV/xls/xlsx multipart `file`；响应无 password） |
| api/users/import | post | 批量导入用户（管理员；`rows`+RSA `password`+`keyId`；≤500） |
| api/users/import/template | get | 下载批量导入 CSV 模板（管理员） |
| api/users/email/verification | get | 邮箱验证（匿名；code；30min 一次性） |
| api/users/appPush/alias | get | 设置 APP 推送别名 |
| api/users/meeting/open | get | 【会议】创建会议、加入会议 |
| api/users/tags/lists | get | 获取个性标签列表（`userId` 缺省自己） |
| api/users/tags/add | post | 新增个性标签（`userId`+`name`；≤100；name≤20） |
| api/users/tags/update | post | 修改个性标签（仅创建者；`id`+`name`） |
| api/users/tags/delete | post | 删除个性标签（创建者或管理员；`id`） |
| api/users/tags/recognize | post | 认可/取消认可个性标签（`id`） |
| api/users/meeting/link | get | 【会议】获取分享链接 |
| api/users/meeting/tourist | get | 【会议】游客信息；参数 `touristId` |
| api/users/meeting/invitation | get | 【会议】发送邀请 |
| api/users/email/send | get | 重发邮箱验证链接（Bearer；无 SMTP 时 `devCode`） |
| api/users/email/edit | get | 申请改邮箱（Bearer；email→验证链接） |
| api/users/delete/account | get | 删除帐号 |
| api/users/department/list | get | 部门列表（限管理员） |
| api/users/department/add | get | 新建、修改部门（限管理员；`parentId`/`ownerUserId`） |
| api/users/department/addDeputy | post | 任命部门管理员（限管理员） |
| api/users/department/deleteDeputy | post | 罢免部门管理员（限管理员） |
| api/users/department/delete | get | 删除部门（限管理员） |
| api/users/department/sync | get | 同步部门成员（限管理员） |
| api/users/attendance/get | get | 获取签到设置 |
| `api/users/attendance/save` | post | 保存签到；`macAddresses?` · `punch?` · `latitude?` · `longitude?` · `faceUploadObjectId?` · `faceCaptureObjectId?` |
| api/users/attendance/list | get | 获取签到数据；查询参数 `yearMonth=yyyy-MM` 可选 |
| api/users/socket/status | get | 获取 socket 状态（本机 WS `fd`） |
| api/users/presence | get | 批量在线态；`userIds` 逗号分隔（≤100）；响应 `items:[{userId,online,pcActive}]` |
| api/users/key/client | get | RSA 公钥 `{ keyId, publicKey, algorithm: RSA-OAEP-SHA256 }`（登录/改密前） |
| api/users/userBot/list | get | 机器人列表 |
| api/users/userBot/info | get | 机器人信息 |
| api/users/userBot/edit | post | 添加、编辑机器人（`clearDay`/`webhookUrl`/`webhookEvents`） |
| api/users/userBot/delete | get | 删除机器人 |
| api/users/share/list | get | 分享选择器；`type`=file\|text · `parentId?` 下钻目录 · `key?` 搜会话/用户；返回 `{type,url,icon,name,extend,sort?}` |
| api/users/annual/report | get | 个人年度报告；`year?` 默认当前年；任务/项目/文件/聊天聚合（camelCase） |
| api/users/device/list | get | 获取设备列表 |
| api/users/device/logout | get | 登出设备（删除设备） |
| api/users/device/edit | get | 编辑设备（`deviceName`/`appBrand`/`appModel`/`appOs`） |
| api/users/task/browse | get | 获取任务浏览历史 |
| api/users/task/browseSave | get | 记录任务浏览历史 |
| api/users/task/browseClean | post | 清理任务浏览历史 |
| api/users/recent/browse | get | 获取最近访问记录（`page`/`pageSize`） |
| api/users/recent/delete | post | 删除最近访问记录 |
| api/users/appSort | get | 获取个人应用排序 |
| api/users/appSort/save | post | 保存个人应用排序 |
| api/users/favorites | get | 获取用户收藏列表 |
| api/users/favorite/toggle | post | 切换收藏状态 |
| api/users/favorite/remark | post | 修改收藏备注 |
| api/users/favorites/clean | post | 清理用户收藏 |
| api/users/favorite/check | get | 检查收藏状态 |

## project

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/project/lists | get | 项目列表；`archived`/`type`/`name`/`keys` 筛选（`type`=all\|team\|personal） |
| api/project/one | get | 获取一个项目信息 |
| api/project/add | get | 添加项目（`isPersonal?`；可选 `columns` 逗号列名，空则默认三列） |
| api/project/update | get | 修改项目（含归档策略 / 模板共享 / 负责人视角 / AI） |
| api/project/user | post | 修改项目成员；`userIds`/`removeUserIds` |
| api/project/invite | get | 获取邀请链接 |
| api/project/invite/info | get | 通过邀请链接 code 获取项目信息 |
| api/project/invite/join | get | 通过邀请链接 code 加入项目 |
| api/project/transfer | get | 移交项目 |
| api/project/addDeputy | post | 任命项目管理员（仅负责人可操作） |
| api/project/deleteDeputy | post | 罢免项目管理员（仅负责人可操作） |
| api/project/sort | post | 看板排序：`onlyColumn` 排列 / 否则排任务并可换列；换列时同步绑定列的工作流节点；`sort=[{id,task[]}]` |
| api/project/user/sort | post | 本人项目列表排序；`list`=[projectId,…] |
| api/project/exit | get | 退出项目 |
| api/project/archived | get | 归档或取消归档；`type=add|recovery` |
| api/project/remove | get | 删除项目 |
| api/project/column/lists | get | 获取任务列表 |
| api/project/column/add | get | 添加任务列表 |
| api/project/column/update | get | 修改任务列表 |
| api/project/column/remove | get | 软删列；级联软删列内任务；至少保留一列 |
| api/project/column/one | get | 列详情；`columnId`；须项目成员 |
| api/project/task/lists | get | 任务列表 |
| api/project/user/projects | get | 指定会员参与的项目列表；`userId` · `archived?` · `keys.name?` · `page?` · `pageSize?`；本人/管理员全量，部门负责人只读范围（`departmentReadonly`） |
| api/project/user/tasks | get | 会员参与的任务列表；本人/管理员/部门负责人只读 |
| api/project/user/counts | get | 会员参与的项目/任务数量 `{project,todo,done}` |
| api/project/task/easyLists | get | 任务简表（计划冲突；`userId`/`userIds` + 可选 `timeRange`/`excludeTaskId`） |
| api/project/task/export | get | 管理员异步导出任务统计；Kafka → CSV → `down?key=` |
| api/project/task/exportOverdue | get | 管理员异步导出超期任务 |
| api/project/task/download | get | 下载导出文件（key 24h，仅本人） |
| api/project/task/one | get | 获取单个任务信息 |
| api/project/task/subtaskData | get | 获取子任务数据 |
| api/project/task/related | get/post | 获取关联列表；POST 手动建立双向关联（`relatedTaskId`） |
| api/project/task/related/delete | post | 删除任务关联（双向） |
| api/project/task/content | get | 获取任务详细描述（`historyId?`；无则 `{}`） |
| api/project/task/contentHistory | get | 获取任务详细历史描述（`items`+`meta`） |
| api/project/task/files | get | 获取任务文件列表 |
| api/project/task/fileDelete | get | 删除任务文件 |
| api/project/task/fileDetail | get | 获取任务文件详情 |
| api/project/task/fileDownload | get | 下载任务文件 |
| api/project/task/add | post | 添加任务；`columnId?`（省略=首列）；可选 `templateId` · `visibility` · `visibilityUserIds` · `description` · `loop`（0–4；>0 须 `endAt`） |
| api/project/task/addSubtask | get | 添加子任务 |
| api/project/task/upgrade | get | 子任务升级为主任务 |
| api/project/task/update | post | 修改；可选 `owner`/`assist`/`content`/`visibility`/`visibilityUserIds`/`tagIds`/`description`/`loop`；`complete=1` 且循环开启时生成下一份 |
| api/project/task/dialog | get | 创建/获取任务群；按 visibility 同步成员；须对本任务可见；缺省群名 `task.group_default_name` |
| api/project/task/archived | get | 归档任务 |
| api/project/task/remove | get | 删除任务 |
| api/project/task/calendar | get | 日历：当前用户任务时间区间查询 |
| api/project/task/resetFromLog | get | 按日志重置任务工作流；`id`=日志；需 `record.flow` |
| api/project/task/flow | get | 任务工作流信息；可选 `flowItemId` 流转 |
| api/project/task/move | get | 任务移动（主任务；`projectId`+`columnId`；可选 `completed`；子任务随迁） |
| api/project/task/copy | post | 复制主任务（`projectId`+`columnId`；可选 `ownerUserId`/`completed`；含子任务与附件元数据） |
| api/project/task/aiGenerate | any | 手动生成 AI 建议（主任务；事件表；外模优先 / 启发式降级；任务群 `:::ai-action{…}:::`，属性键保留 camelCase / snake_case） |
| api/project/ai/generate | any | 废弃占位 `{deprecated:true}` |
| api/project/flow/list | get | 工作流列表（含节点） |
| api/project/flow/save | post | 保存工作流；空 items 套默认 5 节点 |
| api/project/flow/delete | get | 软删工作流 |
| api/project/log/lists | get | 项目/任务动态；`projectId`/`taskId` 二选一；分页 `{items,meta}` |
| api/project/top | get | 切换本人项目置顶；`{ id, topAt }` |
| api/project/permission | get | 获取项目权限矩阵（默认值兜底） |
| api/project/permission/update | get | 更新权限矩阵；`permissions` JSON |
| api/project/task/templateList | get | 任务模板列表 |
| api/project/task/templateVisible | get | 当前用户跨项目可见的全部任务模板 |
| api/project/task/templateSearch | get | 跨项目模板搜索分页（`items`+`meta`；按 useCount 降序） |
| api/project/task/templateSave | post | 保存任务模板 |
| api/project/task/templateSort | post | 排序任务模板 |
| api/project/task/templateDelete | get | 删除任务模板 |
| api/project/task/templateDefault | get | 设置(取消)任务模板为默认 |
| api/project/tag/save | post | 保存标签；name≤20；同名拒绝 |
| api/project/tag/sort | post | 标签排序；`list`=[id,…] |
| api/project/tag/delete | get | 软删标签并清关联 |
| api/project/tag/list | get | 标签列表 |
| api/project/task/aiApply | post | 采纳 AI 建议；`taskId`+`messageId`+`type`·`userId?`·`related?`；similar 写关联；回写卡片 status（不压扁属性键名）；响应含 `message` |
| api/project/task/aiDismiss | post | 忽略 AI 建议；参数同 apply；回写卡片 status（不压扁属性键名）；响应含 `message` |

## dashboard

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/dashboard/team/stats | get | 负责人视角统计（可选 `departmentId` 按部门树） |
| api/dashboard/team/tasks | get | 负责人视角任务列表（可选 `departmentId`） |

## system

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/system/setting | get/post | 获取/保存通用系统设置（限管理员；含 `messageRecallLimit`/`messageEditLimit`；`SYSTEM_SETTING=disabled` 时拒绝写入） |
| api/system/setting/email | get/post | 获取/保存邮箱 SMTP 设置（限管理员；密码掩码同 OSS；见 infra/email.md） |
| api/system/setting/meeting | get/post | 获取/保存会议（Agora）设置（限管理员；证书/密钥掩码同 OSS） |
| api/system/setting/ai | any | **废弃**：旧 AI 入口；统一 `aiBot*`；勿实现 |
| api/system/setting/aiBot | get/post | 获取/保存 AI 机器人设置（限管理员；Key 掩码同 OSS；见 infra/ai-assistant.md） |
| api/system/setting/aiBotModels | get | 当前已配模型列表（限管理员） |
| api/system/setting/aiBotDefaultModels | get | 内置推荐模型（限管理员） |
| api/system/setting/attendance | get/post | 获取/保存签到设置（限管理员；`remindIn`/`remindExceed`/`macAddresses` 等） |
| api/system/setting/appPush | get/post | 获取/保存 APP 推送设置（限管理员；Key/Secret 掩码同 OSS） |
| api/system/setting/thirdAccess | get/post | 第三方帐号 / LDAP 设置（限管理员） |
| api/system/setting/thirdAccess/testLdap | get | LDAP 连接测试（限管理员） |
| api/system/setting/file | get/post | 文件设置（上传上限、打包权限等；限管理员） |
| api/system/setting/oss | get/post | 存储引擎 local/云；见 infra/oss-settings.md |
| api/system/oss/check | get | 对象存储连通性检测（限管理员；put 探针 + delete；见 infra/oss-settings.md） |
| api/system/demo | get | 演示帐号（匿名；`bluedock.demo.account`/`password`；→ `{account,password}`；未配置报错） |
| api/system/priority | post | 任务优先级（`type=get|save`；save 限管理员；`list` 含 name/color/days/priority/isDefault） |
| api/system/microAppMenu | post | 自定义应用菜单 |
| api/system/apps/catalog | get | 官方插件目录（管理员；内置清单，无外部商店） |
| api/system/apps/installed | get | 已安装应用列表（管理员；注册表；无本进程 Docker） |
| api/system/apps/install | post | 注册安装应用（管理员；写 `bluedock_installed_apps`；catalog 可补全；联动 microAppMenu；可选 lifecycle Hook） |
| api/system/apps/update | post | 更新已安装应用（管理员；联动 microAppMenu；可选 Hook） |
| api/system/apps/uninstall | post | 卸载应用（管理员；不可卸 appstore；可选 Hook） |
| api/system/column/template | post | 创建项目列模板（`type=get|save`；save 限管理员；`list` 含 name/columns） |
| api/system/license | post | License |
| api/system/get/info | get | 运行信息；→ `{name:BlueDock,version,java,time}` |
| api/system/get/ip | get | 获取 IP 地址 |
| api/system/get/chinaIp | get | 是否中国 IP；响应 `ip` + `isChina`（判定：CDN 国家头 → 可选 `bluedock.system.geoip-mmdb` → 内网/本机启发式） |
| api/system/imageUpload | post | 上传图片（写 `bluedock_upload_objects`；见 infra/upload-objects.md） |
| api/system/imageView | get | 本人图片空间（`bluedock_upload_objects` media；见 infra/upload-objects.md §4.6） |
| api/system/fileUpload | post | 上传文件（同上） |
| api/system/uploads | get/post/delete | 上传库管理（管理员） |
| api/system/get/updateLog | get | 更新日志（匿名；`take?` 默认 50，10–100；读 CHANGELOG；→ `{logVersion,updateLog}`） |
| api/system/email/check | get | 邮件发送测试（限管理员；见 infra/email.md） |
| api/system/attendance/export | get | 管理员异步导出签到；Kafka `kind=attendance` → CSV → `attendance/download?key=` |
| api/system/attendance/download | get | 下载签到导出文件（key 24h，仅本人） |
| api/system/version | get | 产品名与版本；→ `{name:BlueDock,version,publish,deviceCount}` |
| api/system/prefetch | get | 预加载的资源 |

## approve

| URL | HTTP | 说明 |
| --- | ---- | ---- |
| api/approve/export | get | 管理员异步导出审批；`processName` 必填 · `status?` · `date`；须 approve 插件（`ApproveExportBridge`）；Kafka `kind=approve` → `approve/download?key=` |
| api/approve/download | get | 下载审批导出文件（key 24h，仅本人） |

## license

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/license/email/send | get | 在线授权发邮箱验证码（local 返回 `devCode`） |
| api/license/login | get | 邮箱+验证码 → pending token |
| api/license/login/confirm | get | 确认登录并落盘在线 License |
| api/license/trial | get | 本机试用（每 SN 一次） |
| api/license/status | get | License 状态（含 online 字段） |
| api/license/refresh | get | 刷新在线 License 指纹 |
| api/license/logout | get | 退出在线授权 |

## dialog

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/dialog/lists | get | 对话列表 |
| api/dialog/beyond | get | 列表外对话 |
| api/dialog/search | get | 搜索会话 |
| api/dialog/search/tag | get | 搜索标注会话 |
| api/dialog/one | get | 获取单个会话信息 |
| api/dialog/user | get | 获取会话成员 |
| api/dialog/todo | get | 获取会话待办（当前用户；`dialogId` · `includeDone?`） |
| api/dialog/top | get | 会话置顶；`isTop`（默认 1） |
| api/dialog/hide | get | 会话隐藏/恢复；`dialogId`·`isHidden?`（默认1，0=恢复） |
| api/dialog/telephone | get | 单聊对方电话；`dialogId`；临时账号不可查；→ `{ telephone, add }` |
| api/dialog/open/user | get | 打开会话 |
| api/dialog/open/event | get | 打开会话事件（bot dialogOpen；返回会话视图） |
| api/dialog/message/list | get | 获取消息列表 |
| api/dialog/message/latest | get | 多会话增量；`dialogs` JSON（`id`+`latestId?`，≤5）· `take?`（默认 25，≤50） |
| api/dialog/message/one | get | 获取单条消息 |
| api/dialog/message/dot | get | 清红点；`messageId` → `{ messageId, dot:0 }` |
| api/dialog/message/read | get | 已读聊天消息 |
| api/dialog/message/unread | get | 获取未读消息数据 |
| api/dialog/message/checked | get | 文本清单 checked；`dialogId`·`messageId`·`index`·`checked` |
| api/dialog/message/stream | post | 通知用户听流；`userId`·`streamUrl`·`source?` |
| api/dialog/message/aiGenerate | any | **废弃占位**；`{deprecated:true}` |
| api/dialog/message/sendText | post | 发送消息 |
| api/dialog/message/sendNotice | post | 发送 notice；`dialogId`/`dialogIds` · `notice`（≤500）· `silence?` · `source?` |
| api/dialog/message/sendTemplate | post | 模板卡片；`dialogId`/`dialogIds` · `content` JSON 数组 · `title?` · `silence?` · `source?` |
| api/dialog/message/sendApprove | post | 审批卡片；`toUserId` · `type` · `action?` · `isFinished?` · `data?` · `title?`；`approval-alert` 静默 |
| api/dialog/message/sendRecord | post | 语音；`dialogId` · `base64`（mp3/wav data-URL）· `duration`≥600ms · `replyId?` |
| api/dialog/message/convertRecord | post | 录音转写；`base64` · `duration` · `dialogId?` · `translate?`；→ `{ text }` |
| api/dialog/message/sendFile | post | 文件上传（单文件单会话） |
| api/dialog/message/image64 | post | Base64 发图；`dialogId`+`image`（data-URL/裸 base64）·`filename?`·`replyId?`；≤5MB |
| api/dialog/message/sendSticker | post | 在线表情发图；`dialogId`+`src`·`name?`·`replyId?`；服务端拉图≤5MB |
| api/dialog/message/sendFiles | post | 群发文件：multipart `files` + `dialogId`/`dialogIds`（各≤20）→ 消息列表 |
| api/dialog/message/sendFileId | get | 通过文件 ID 发送文件 |
| api/dialog/message/sendTaskId | get | 发任务卡片；`dialogId`+`taskId`；可选 `note`/`text`；须任务可见 |
| api/dialog/message/sendAnon | post | 匿名消息；`userId`+`text`（≤2000）；经 anon-msg 机器人；受 `anonMessage` 开关 |
| api/dialog/message/sendBot | post | 机器人 markdown 私聊；`userId`+`text`+`botType?`（默认 system-msg）· `botName?` · `silence?` |
| api/dialog/message/sendAiAssistant | post | AI 助手发信；`dialogId` 或 `taskId` · `text` · `textType?` · `nickname?` · `silence?`；`ai-openai` 机器人 |
| api/dialog/message/sendLocation | post | 位置；`dialogId` · `type`（baidu/amap/tencent）· `lng` · `lat` · `title` · `distance?` · `address?` · `thumb?` |
| api/dialog/message/readList | get | 获取消息阅读情况 |
| api/dialog/message/detail | get | 消息详情；`messageId` · `onlyUpdateAt?`；file/image 附 `file` |
| api/dialog/message/download | get | 附件下载；`messageId` · `down=yes|preview` |
| api/dialog/message/withdraw | get | 撤回自己的消息；受 `messageRecallLimit`；自聊/机器人豁免 |
| api/dialog/message/voiceToText | get | 已有语音转写；`messageId`；写 `body.text`/`textUserId` |
| api/dialog/message/translation | get | 翻译；`messageId`·`language`·`force?`；text/record；按语言缓存；→ `{ messageId, language, content }` |
| api/dialog/message/mark | get | 会话标记已读/未读；`dialogId`·`type=read\|unread`·`afterMessageId?`；→ 未读快照含 `markUnread` |
| api/dialog/message/silence | get | 消息免打扰；`isSilent`（默认 1） |
| api/dialog/message/forward | get | 转发消息给 |
| api/dialog/message/mergeForward | get | 合并转发消息 |
| api/dialog/message/mergeDetail | get | 合并转发详情；`messageId` → `{ messages }` |
| api/dialog/message/emoji | get | emoji 回复 |
| api/dialog/message/emojiMap | get | 批量表情聚合（`messageIds`） |
| api/dialog/message/tag | get | 消息标注切换；`messageId`；→ `{ messageId, tag, add }`（与会话 `tag` 区分） |
| api/dialog/message/todo | get | 设待办/取消待办 |
| api/dialog/message/todoList | get | 获取消息待办情况 |
| api/dialog/message/todoRemind | post | 设置/修改/取消待办提醒时间 |
| api/dialog/message/done | get | 完成待办 |
| api/dialog/message/color | get | 当前用户会话颜色；`dialogId`·`color?`；存成员表；→ `{ dialogId, color }` |
| api/dialog/message/webhookMessageToAi | any | **废弃占位**；`{deprecated:true}` |
| api/dialog/group/add | get | 新增群组 |
| api/dialog/group/edit | get | 修改群组 |
| api/dialog/group/addUser | get | 添加群成员 |
| api/dialog/group/deleteUser | get | 移出（退出）群成员 |
| api/dialog/group/transfer | get | 转让群组 |
| api/dialog/group/addDeputy | any | 任命群管理员（→ 管理员 userId 列表） |
| api/dialog/group/deleteDeputy | any | 罢免群管理员（→ 管理员 userId 列表） |
| api/dialog/group/deputies | get | 普通群管理员列表 |
| api/dialog/group/disband | get | 解散群组 |
| api/dialog/group/searchUser | get | 搜索个人群（仅系统管理员；`key?` → `{list}` 最多 20） |
| api/dialog/common/list | get | 共同/本人普通个人群（`targetUserId?` · `onlyCount?` · `page`/`pageSize`） |
| api/dialog/okr/add | post | 创建/复用 OKR 评论群；`okrId` · `name?` · `userIds?`；自动加入 `okr-alert@bot.system` |
| api/dialog/okr/push | post | OKR 提醒机器人发文；`dialogId` 或 `okrId` · `text`；调用者须为成员 |
| api/dialog/message/wordChain | post | 发送接龙消息 |
| api/dialog/message/vote | post | 发起投票 |
| api/dialog/message/top | get | 置顶/取消置顶 |
| api/dialog/message/topInfo | get | 获取置顶消息 |
| api/dialog/message/applied | any | **废弃占位**；`{deprecated:true}` |
| api/dialog/sticker/search | get | 在线表情；`key?` → `{ list:[{name,src,height,width}] }` |
| api/dialog/config | get | 获取会话配置（`isMuted`/`isTop`/`isHidden`/`tag`/`isChatMuted`） |
| api/dialog/config/save | post | 保存会话配置（`isMuted?`/`tag?`/`isChatMuted?`；群禁言仅群主/管理员） |
| api/dialog/session/create | get | AI-开启新会话 |
| api/dialog/session/list | get | AI-获取会话列表 |
| api/dialog/session/open | get | AI-打开会话 |
| api/dialog/session/rename | post | AI-重命名会话 |

## file

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/file/lists | get | 获取文件列表 |
| api/file/one | get | 获取单条数据 |
| api/file/fetch | get | 通过路径获取文件文本内容 |
| api/file/search | get | 搜索文件列表 |
| api/file/add | get | 添加、修改文件(夹) |
| api/file/copy | get | 复制文件(夹) |
| api/file/move | get | 移动文件(夹) |
| api/file/remove | get | 删除文件(夹) |
| api/file/trash | get | 回收站根列表（当前用户软删且父级未删） |
| api/file/restore | get | 恢复软删文件(夹)及子树；父已删则挂根 |
| api/file/raw | get | 鉴权流式读取文件二进制（图片预览等，非信封） |
| api/file/content | get | 获取文件内容 |
| api/file/content/save | get | 保存文件内容 |
| api/file/office/token | get | 获取 token |
| api/file/content/office | get | 保存文件内容（office） |
| api/file/content/upload | get | 保存文件内容（上传文件） |
| api/file/content/history | get | 获取内容历史 |
| api/file/content/restore | get | 恢复文件历史 |
| api/file/share | get | 获取共享信息（`isShared`） |
| api/file/share/update | get | 设置共享；`userIds`/`removeUserIds` |
| api/file/share/out | get | 退出共享 |
| api/file/link | get | 获取链接；`allowGuest?` |
| api/file/download/pack | get | 打包文件 |

## upload

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/upload/init | post | 启动上传会话；`scene`=`file_cabinet`\|`project_task`；后者需 `taskId`；`parentId?` |
| api/upload/chunk | post | 上传一个分片 |
| api/upload/merge | post | 合并分片；响应 `scene` + `file` 或 `taskFile` |
| api/upload/cancel | post | 取消上传会话 |

## report

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/report/my | get | 我发送的汇报 |
| api/report/receive | get | 我接收的汇报 |
| api/report/store | get | 保存并发送工作汇报 |
| api/report/template | get | 生成汇报模板（标题/sign/任务汇总正文；负责人任务） |
| api/report/aiGenerate | post | AI 整理草稿（须已有 content；`aiBotSetting` OpenAI 兼容；失败 `report.ai_*`） |
| api/report/detail | get | 报告详情（`id` 或分享 `code`；含本人 `aiAnalysis`） |
| api/report/analysisSave | post | 保存工作汇报 AI 分析（按查看者；`analysisText`/`text`） |
| api/report/mark | get | 标记已读/未读 |
| api/report/share | get | 分享报告到会话（短码 + 消息；`dialogId`/`dialogIds`） |
| api/report/lastSubmitter | get | 获取最后一次提交的接收人 |
| api/report/unread | get | 获取未读 |
| api/report/read | get | 标记汇报已读，可批量 |

## public

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/privacy | get | 隐私政策 HTML（匿名；`text/html`） |
| api/public/attendance/install | any | 签到 WiFi 安装指引 |
| api/public/attendance/report | any | WiFi 自动打卡；`macAddress`+`key` |
| api/public/attendance/face | post | 人脸设备打卡；`userId`+`faceCaptureObjectId`+`key` |

## assistant

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/assistant/auth | post | 生成授权码 |
| api/assistant/models | get | 获取 AI 模型 |
| api/assistant/matchElements | post | 元素匹配；优先 embedding 余弦，回退词法；`{matches,strategy}` |
| api/assistant/log/search | post | 记录帮助知识库检索日志 |
| api/assistant/feedback/save | post | 保存回复反馈 |
| api/assistant/operation/dispatch | post | 派发页面操作 |
| api/assistant/operation/result | get | 取页面操作结果 |
| api/assistant/session/list | any |  |
| api/assistant/session/save | any |  |
| api/assistant/session/delete | any |  |

## ai（助手流式运行时）

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/ai/invoke/stream/{streamKey} | get | SSE 流式对话；消费 `assistant/auth` 签发的一次性 `streamKey`（匿名，靠 key）；事件 `append`/`done`，data JSON `{content}` / `{error?}` |

## complaint

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/complaint/lists | get | 管理员分页列表（`{list,page,pageSize,total}`） |
| api/complaint/submit | post | 成员举报会话（`dialogId`·`type`·`reason`·`images?`） |
| api/complaint/action | post | 管理员 `handle` / `delete` |

## search

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/search/contact | get | 搜索联系人 |
| api/search/project | get | 搜索项目 |
| api/search/task | get | 搜索任务 |
| api/search/file | get | 搜索文件 |
| api/search/message | get | 搜索消息 |
| api/search/rebuild | post | 全量重建索引（限管理员；`types` 可选） |
| api/search/rebuild/status | get | 重建进度（限管理员） |

## apps

| URL | HTTP | 说明 |
| --- | --- | --- |
| api/apps/badge/set | post | 设置角标（应用密钥鉴权；`appId`/`userId`/`menuKey`） |
| api/apps/badge/clear | post | 清除角标（当前用户 token 鉴权；`appId`/`menuKey`） |
| api/apps/badge/list | get | 拉取自己全部角标 |

---

## 能力缺口（parity）

对照口径：产品能力清单 + 历史 `/api/{resource}/{method}` 路径；实现以本仓 Controller 与上表为准。

> **结论**：上表业务 REST 主路径、调度与非 `/api` 工具端点（`/avatar`、`/drawio/iconsearch`、`/online/preview`）已齐（命名按全词扩写，见 [naming.md](naming.md)）。剩余开放项为插件本体与发版回归。详见 [ops/migration.md](../ops/migration.md)。

### 对照方法

| 项 | 目标 / 历史形态 | 本仓 |
| -- | -------------- | ---- |
| 路由 | `api/{resource}/{method}` · `{method}/{action}` | Spring `@RequestMapping("/api/...")` |
| 命名 | 历史简写（`msg`/`checkin`/`editpass`/`cnip`…） | **全词 camelCase**（`message`/`attendance`/`editPassword`/`chinaIp`…） |
| 异步 | 分钟级 HTTP 触发全量后台任务（`/crontab`） | `@Scheduled` + Kafka Worker + Outbox |
| 插件 | 本进程 Docker 应用目录编排 | 注册表 + 可选 HTTP Hook；**无本进程 Docker** |

历史 public 端点约 314 个；经简写→全词归一后与本仓交集约 312。假阳性：`setting/ai`（废弃→`aiBot*`）、`task/related__delete`（本仓 `task/related/delete`）。

### 已齐（摘要）

- 用户 / 部门 / 登录注册验证码扫码 / 设备 / 导入 / 收藏最近 / 标签 / bot
- 项目 / 列 / 工作流 / 标签 / 权限矩阵 / 邀请移交
- 任务 CRUD、子任务、模板、循环（**完成时生成下一期**）、AI 建议 API、日历、导出
- 会话消息全路径（含投票/接龙/待办设置/翻译/机器人发送等）；废弃 AI 占位保留
- 文件 / 分片上传 / 报告 / 会议 / 签到桥接 / 仪表盘
- 系统设置（含 `aiBot*`、OSS、邮件、会议、推送、LDAP）/ License / 搜索 / 助手流式
- 投诉 / apps 角标 / AppStore 注册表 / 微应用菜单
- WS presence + Kafka fanout；通知 Worker（邮件/APP 推送）

废弃对齐：`/api/system/setting/ai` → 用 `aiBot*`；部分 dialog AI 路径返回 `{deprecated:true}`。

### 定时 / 后台任务

本仓**无** `/crontab` HTTP；已用 `@Scheduled` / Kafka 替代的见「本仓状态」。

| 能力 | 说明 | 本仓状态 |
| ---- | ---- | -------- |
| 自动归档 | `autoArchive` 到期归档已完成任务 | **已落地**：`TaskAutoArchiveScheduler`（系统/项目 custom） |
| 未领取提醒 | `unclaimedTaskReminder` | **已落地**：`UnclaimedTaskRemindScheduler` + 设置 `unclaimedTaskReminder`/`Time` |
| 待办到期推送 | 消息待办 `remindAt` | **已落地**：`DialogTodoRemindScheduler` → todo-alert 私聊 |
| 机器人消息清理 | 按 bot `clearDay` 清理 | **已落地**：`UserBotClearDayScheduler` + `clear_at` 水位 |
| 任务 AI 自动扫描 | 新建任务延迟自动分析 | **已落地**：`TaskAiScanScheduler` + 创建后 `scheduleAfterCreate` |
| AI 会话标题 | 对话侧会话标题自动生成 | **已落地**：首条文本预览 + AI 精炼（`DialogSessionTitleService`） |
| 循环任务生成 | 循环任务到期/完成生成下一期 | **已替代**：完成时 `maybeSpawnRecurring`（语义略异于纯到期扫描） |
| 邮件汇总 / APP 推送 | 未读邮件、友盟推送 | **已替代**：UnreadEmailNoticeScheduler + Worker |
| 签到提醒 | 缺卡提醒 | **已替代**：`AttendanceRemindScheduler` |
| 会议关房 | 超时关房 | **已替代**：`CloseMeetingRoomScheduler` |
| 临时文件清理 | 上传临时盘等 | **部分替代**：`UploadTempCleanupScheduler` 等 |
| 搜索同步 | 全文索引增量 | **已替代**：Kafka `SEARCH_INDEX` + rebuild API |
| 上下线推送 | presence | **已替代**：WS presence / `presence.*` |
| WS 扇出 | 多实例消息投递 | **已替代**：realtime + Kafka fanout |

### 非 `/api` 页面 / 工具端点

| 路径 | 说明 | 本仓状态 |
| ---- | ---- | -------- |
| `/avatar` | 字母头像 PNG 生成 | **已落地**：`GET /avatar` |
| `/drawio/iconsearch` | Draw.io 图标搜索代理 | **已落地**：`GET /drawio/iconsearch` |
| `/online/preview` | PDF 等在线预览页 | **已落地**：`GET /online/preview` |
| `/` · SPA · `/api`→docs | 前端壳与文档跳转 | **本仓不做前端**；健康检查有 `/api/health` |
| `/version` | 301 → `api/system/version` | 直接调 `GET /api/system/version` |

### 架构 / 产品明确差异（非遗漏）

| 项 | 历史 / 插件形态 | 本仓 |
| -- | -------------- | ---- |
| AppStore | 本进程读应用目录、可装镜像 | 注册表 + Hook；**无本进程 Docker** |
| face / approve / office / drawio / minder / okr 等 | 插件进程 | 桥接 + 目录登记 |
| 审批业务 CRUD | approve 插件 | 仅导出桥接 `/api/approve/export|download` + 消息 `sendApprove` |
| JSON 字段名 | 历史简写混用 | **禁止简写**（契约强制） |
| 搜索引擎 | 历史全文引擎 | mysql / docs / opensearch 可切换 |

### 本仓增强（相对历史主路径）

示例：`/api/ai/invoke/stream/{streamKey}`、`/api/system/setting/oss` · `oss/check`、`/api/system/uploads`、`/api/search/rebuild*`、`/api/users/presence`、`file/trash|restore`、LDAP `testLdap` 等（以上表为准）。

### 开放项优先级

| 优先级 | 项 |
| ------ | -- |
| P0 | ~~自动归档；待办到期；机器人 clearDay~~ **已落地** |
| P1 | ~~未领取任务提醒；任务 AI 自动扫描~~ **已落地** |
| P2 | ~~AI 会话标题；`/avatar`；`/drawio/iconsearch`；`/online/preview`~~ **已落地** |
