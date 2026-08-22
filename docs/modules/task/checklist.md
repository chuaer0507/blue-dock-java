# 任务 — 验收清单

## 创建与编辑
- [x] 快建 / 全量建 / 对话内 `#` 创建（快建可省略 `columnId`；`@#` 引用 + `sendTaskId` 任务卡片已落地）
- [x] 编辑标题、描述、优先级、颜色、标签（`update?tagIds=`）、时间；富文本详情 `content` / `contentHistory`（经 `update?content=`）
- [x] 负责人 / 协助人变更（`update?owner=` / `assist=`）
- [x] 可见性 1/2/3；`visibilityUserIds`；子任务继承父可见性；列表/详情按可见过滤

## 流转
- [x] 完成 / 取消完成（`update?complete=`）
- [x] 换列（`update?columnId=` / `move`）、跨项目移动（`move`）；列内排序 `POST /api/project/sort`；列绑定工作流节点时拖列联动 `flowItem`
- [x] 工作流节点流转（`task/flow?flowItemId=` / `update?flowItemId=`；配置见 `flow/*`）
- [x] 子任务增删；升级为主任务（`upgrade`）
- [x] 归档 / 删除；按日志恢复工作流（`resetFromLog`，依赖流转时 `record.flow` 快照）
- [x] 复制任务（`copy`）；关联任务（`related` / `related/delete`；任务群消息 `@#` → `TaskMentionBridge`）

## 附件与对话
- [x] 附件列表 / 详情 / 软删；`upload` scene=`project_task` 写入
- [x] 打开任务聊天室；消息可见范围正确（`task/dialog` 按 visibility 同步成员；messenger 读写经 `TaskDialogAccessBridge` 校验；vis 变更重同步；机器人成员保留）

## 模板与 AI
- [x] 模板保存 / 列表 / 搜索分页 / 默认 / 排序 / 删除 / 跨项目可见；创建任务传 `templateId` 计次
- [x] AI 建议生成 / 采纳 / 忽略（`aiGenerate` / `aiApply` / `aiDismiss`；事件表；优先外模 + 启发式降级；任务群 Markdown 卡片经 `TaskAiDialogBridge`）

- [x] 循环 / 重复任务（`loop`/`loopAt`；完成时生成下一份；见 [recurring.md](recurring.md)）

详见 [overview.md](overview.md) · [permissions.md](permissions.md)。
