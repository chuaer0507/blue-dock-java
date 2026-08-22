# 任务 — API

任务挂在 `api/project/task*`（历史路径；领域仍属 `bluedock-task` 模块）。

## 已实现（骨架）

| URL | 鉴权 | 说明 |
| --- | ---- | ---- |
| `GET /api/project/task/lists` | Bearer | 主任务列表；`projectId` · `columnId?` · `includeArchived?`；按当前用户可见性过滤；项含 `tagIds` / `ownerUserIds` / `assistUserIds`（及 vis=3 时 `visibilityUserIds`） |
| `GET /api/project/task/easyLists` | Bearer | 计划冲突简表；`userId`/`userIds` · `timeRange?` · `excludeTaskId?` · `limit?`；未完成且负责人命中 |
| `GET /api/project/task/one` | Bearer | 详情；`taskId`；须项目成员且对该任务可见；`visibility=3` 时含 `visibilityUserIds`；含 `ownerUserIds` / `assistUserIds` |
| `GET /api/project/task/dialog` | Bearer | 创建/获取任务群；按 visibility 同步成员（1=项目成员 · 2=任务人员 · 3=任务人员+指定可见用户）；写回 `dialogId`；仅主任务；须对本任务可见；缺省群名走 `task.group_default_name`（zh「任务」/ en `Task`） |
| `POST /api/project/task/add` | Bearer | 创建；`projectId` · `columnId?`（省略则落项目首列，便于快建）· `name` · `description?` · `color?` · `visibility?`（1/2/3）· `visibilityUserIds?`（vis=3 指定成员，逗号分隔）· `ownerUserId?` · `startAt?` · `endAt?` · `loop?`（0=关 · 1=天 · 2=周 · 3=月 · 4=年；>0 须 `endAt`；仅主任务）· `templateId?`（套用则递增 `useCount`） |
| `GET /api/project/task/flow` | Bearer | 工作流信息；`taskId` · 可选 `flowItemId` 执行流转（写 `record.flow` 快照日志）；流转至 end 完成时若启用循环则生成下一份 |
| `GET /api/project/task/resetFromLog` | Bearer | 按日志重置工作流；`id`=日志 ID；仅支持含 `record.flow` 的状态变更日志 |
| `POST /api/project/task/update` | Bearer | 修改；… · `owner?`/`assist?`（逗号分隔 userId，全量替换；owner 至少 1 人 ≤10；assist 仅主任务 ≤10）· `tagIds?` · `loop?`；`complete=1` 且启用循环时生成下一周期主任务（见 [recurring.md](recurring.md)） |
| `GET /api/project/task/content` | Bearer | 最新详情或 `historyId` 指定历史；无内容返回 `{}` |
| `GET /api/project/task/contentHistory` | Bearer | 历史摘要分页；`taskId` · `page?` · `pageSize?` → `{items,meta}` |
| `GET /api/project/task/addSubtask` | Bearer | 子任务；`taskId`（父）· `name` · `description?` · `ownerUserId?`；继承父可见性/列；最多 50；仅一层 |
| `GET /api/project/task/subtaskData` | Bearer | 子任务列表；`taskId` |
| `GET /api/project/task/archived` | Bearer | 归档；`taskId` · `follow?=true` 级联子任务 |
| `GET /api/project/task/remove` | Bearer | 软删；父任务级联软删子任务 |
| `GET /api/project/task/move` | Bearer | 换列/跨项目移动主任务；`taskId` · `projectId` · `columnId` · `completed?`；子任务一并迁移；`completed=1` 且启用循环时生成下一份；返回主+子列表 |
| `GET /api/project/task/upgrade` | Bearer | 子任务升级为主任务；`taskId`；继承父优先级字段 |
| `POST /api/project/task/copy` | Bearer | 复制主任务；`taskId` · `projectId` · `columnId` · `ownerUserId?` · `completed?`；含子任务与附件元数据 |
| `GET /api/project/task/related` | Bearer | 关联列表；`taskId` → `{taskId,items:[{relatedTaskId,mention,mentionedBy,latestAt,latestMessageId,task}]}` |
| `POST /api/project/task/related` | Bearer | 手动双向关联；`taskId` · `relatedTaskId`；任务群文本消息中 `@#` / `<span class="mention task">` 亦经 `TaskMentionBridge` 写同一表 |
| `POST /api/project/task/related/delete` | Bearer | 删除双向关联；`taskId` · `relatedTaskId` |
| `GET /api/project/task/files` | Bearer | 附件列表；`taskId` |
| `GET /api/project/task/fileDetail` | Bearer | 附件详情；写入最近访问；`fileId` · `onlyUpdateAt?` |
| `GET /api/project/task/fileDelete` | Bearer | 软删附件；`fileId` |
| `GET /api/project/task/fileDownload` | Bearer | 下载元数据（path/url）并计数；`fileId` |
| `GET /api/project/task/templateList` | Bearer | 项目内模板；`projectId` |
| `GET /api/project/task/templateVisible` | Bearer | 跨项目可见模板；`currentProjectId?` |
| `GET /api/project/task/templateSearch` | Bearer | 跨项目关键字搜索分页；`keyword?` · `currentProjectId?` · `page?` · `pageSize?`（默认 20，最大 50）；按 `useCount`/`lastUsedAt` 降序；`data={items,meta}` |
| `POST /api/project/task/templateSave` | Bearer（管理） | 新建/更新；`projectId` · `id?` · `name` · `title?` · `content?` |
| `POST /api/project/task/templateSort` | Bearer（管理） | body `{list:[id…]}` · `projectId` |
| `GET /api/project/task/templateDelete` | Bearer（管理） | `id` |
| `GET /api/project/task/templateDefault` | Bearer（管理） | 切换默认；`id` · `projectId` |
| `GET /api/project/task/export` | Bearer（系统管理员） | 异步导出任务统计；`userId`/`userIds`（≤100）· `time`（≤90 天）· `type?=taskTime\|createdTime` |
| `GET /api/project/task/exportOverdue` | Bearer（系统管理员） | 异步导出全站超期未完成 |
| `GET /api/project/task/download` | Bearer | 下载导出；`key`（24h） |
| `GET /api/project/user/counts` | Bearer | 会员参与项目/任务数量；`userId` · `owner?`（0 协助 / 1 负责）→ `{project,todo,done}` |
| `GET /api/project/user/tasks` | Bearer | 会员参与任务分页；`userId` · `owner?` · `projectId?` · `keys?`（JSON name/status）· `page`/`pageSize` → `{items,meta}` |
| `GET\|POST /api/project/task/aiGenerate` | Bearer | 手动生成 AI 建议；`taskId`（主任务）；写 `bluedock_task_ai_events`；优先 `aiBotSetting` 外模，失败回退启发式；有建议时经 `TaskAiDialogBridge` 投递任务群 Markdown（`:::ai-action{type=… task_id=… message_id=… userId?=…}:::`；属性键保留 camelCase / snake_case 原样，按 status 回写时不压扁键名） |
| `POST /api/project/task/aiApply` | Bearer | 采纳；`taskId` · `messageId` · `type`（description/subtasks/assignee/similar）· `userId?` · `related?`；similar 写双向关联；返回 `{type,taskId,result,message}`（`message` 为更新 status 后的对话消息视图，无桥时 null） |
| `POST /api/project/task/aiDismiss` | Bearer | 忽略；参数同 apply |
| `GET\|POST /api/project/ai/generate` | Bearer | **废弃占位**；返回 `{deprecated:true}` |

时间参数格式：`yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss`。负责人写入 `bluedock_task_users.owner=1`。

## 规划中

| URL | 说明 |
| --- | ---- |
| （暂无） | |

权限 / 可见性见 [permissions.md](permissions.md)。
