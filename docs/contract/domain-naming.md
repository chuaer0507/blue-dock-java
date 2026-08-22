# 领域命名

业务实体与 API / 表 / 前端 `packages/shared` 共用同一套英文名（camelCase wire，snake_case 落库）。

## 核心实体

| 中文 | 英文（领域） | 表前缀示意 | 说明 |
| ---- | ------------ | ---------- | ---- |
| 用户 | User | `bluedock_users` | 账号、资料 |
| 部门 | Department | `bluedock_user_departments` | 组织树 |
| 项目 | Project | `bluedock_projects` | 团队/个人项目 |
| 项目成员 | ProjectUser | `bluedock_project_users` | `owner`：0 成员 / 1 拥有者 / 2 管理员 |
| 列 | ProjectColumn | `bluedock_project_columns` | 看板分栏 |
| 任务 | Task / ProjectTask | `bluedock_tasks` | 主任务 / 子任务（`parentId`） |
| 任务成员 | TaskUser | `bluedock_task_users` | 负责人 / 协助 |
| 工作流 | ProjectFlow / FlowItem | `bluedock_project_flows` | 节点与流转 |
| 会话 | Dialog | `bluedock_dialogs` | 单聊 / 群 / 项目群 / 任务群 |
| 消息 | DialogMessage | `bluedock_dialog_messages` | IM 消息 |
| 文件 | File | `bluedock_files` | 个人文件树 |
| 报告 | Report | `bluedock_reports` | 日报 / 周报 |
| 签到 | Attendance | `bluedock_user_attendance_records` 等 | 打卡 / 考勤；包名 `attendance` |
| 收藏 | Favorite | `bluedock_user_favorites` | 收藏对象 |
| 标签 | Tag | 项目内标签 / 个性标签 | 注意区分项目任务标签与用户个性标签 |

## 角色用语

| 用语 | 含义 |
| ---- | ---- |
| 超管 | 系统内置最高管理员（实现约定见角色文档） |
| 系统管理员 | admin 身份，可进管理后台 |
| 项目拥有者 / 负责人 | `ProjectUser.owner=1`，每项目唯一 |
| 项目管理员 | `owner=2` |
| 任务负责人 | 任务上的 leader |
| 协作者 / 协助人 | 任务 assist |
| 部门负责人 / 部门管理员 | 组织侧角色，与项目角色独立 |

## 命名铁律

1. JSON wire **camelCase 全词**（`projectId`、`pageSize`、`userImage`）——**禁止简写**，见 [naming.md](naming.md)
2. 物理列 **snake_case 全词**（`project_id`、`user_img`）
3. 禁止同一概念两套英文名（如勿混用 `group` 与 `dialog` 指会话——会话统一 `Dialog`）
4. 改名须同步：`naming.md` 词表、`api-contract.md`、前端 `packages/shared`、`.agents/rules/`

详见 [naming.md](naming.md) 与各模块 `overview.md`。
