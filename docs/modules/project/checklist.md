# 项目 — 验收清单

图例：`[ ]` 待测 · `[x]` 通过

## 项目本体
- [x] 创建团队项目（自动建项目群、默认列/可选工作流）
- [x] 创建个人项目（每用户限 1；无协作群）
- [x] 编辑名称/描述/归档策略等设置（`update`：archiveMethod/days · aiAutoAnalyze · taskTemplateShare · departmentOwnerView）
- [x] 看板排序 `POST /api/project/sort`（列 / 任务）
- [x] 列表排序与置顶（`user/sort` · `top`，每用户独立）
- [x] 归档 / 删除 / 恢复（`archived?type=add|recovery` · `remove`；归档级联任务）
- [x] 搜索与筛选（`lists`：`archived` · `type` · `name`/`keys.name`）

## 成员与角色
- [x] 邀请链接加入
- [x] 添加 / 移除成员（群成员同步）
- [x] 任命 / 罢免管理员（仅拥有者）
- [x] 移交拥有者
- [x] 主动退出（拥有者不可直接退）

## 列 / 工作流 / 权限
- [x] 列增删改排序；删列级联软删列内任务、至少保留一列（`column/remove` · `column/one` · `column/update` · `project/sort`）
- [x] 启用工作流；节点配置与任务流转（`flow/list|save|delete` · `task/flow`）；看板拖列联动绑定列的 `flowItem`（`sort` / `update` / `move`）
- [x] 权限矩阵：`permission` / `permission/update`；拥有者/管理员全开；普通成员按矩阵；已接入列 CRUD、看板排序、任务增改删归档移动
- [x] 项目/任务动态：`log/lists`；关键写点（创建/改名/成员/列/标签/排序/任务增改删归档移动等）

## 导出
- [x] 任务统计 / 超期导出（管理员）异步完成并通知（`export` · `exportOverdue` · `down`；Kafka `bluedock.export.run` → notify worker 出 CSV）

## 会员参与项目
- [x] `GET /api/project/user/projects`（`userId` · `archived?` · `keys.name?` · 分页；本人/管理员全量，部门负责人只读 `departmentReadonly`）

详见 [overview.md](overview.md) · [permissions.md](permissions.md) · [api.md](api.md)。
