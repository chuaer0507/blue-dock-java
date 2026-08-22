# 任务 — 循环 / 重复

> 产品能力 `task.recurring`。实现以本页 + `api.md` + 契约为准。

## 规则

| 项 | 说明 |
| -- | ---- |
| 字段 | `loop`（0=关 · 1=天 · 2=周 · 3=月 · 4=年）· `loopAt`（下一周期计划截止，通常等于 `endAt`） |
| 前提 | 仅**主任务**；`loop>0` 必须有 `endAt` |
| 生成时机 | **刚标记完成**时生成下一份（`update?complete=1`、工作流 `status=end`、`move?completed=1`） |
| 非预生成 | 不按日历提前批量生成实例 |
| 下一份内容 | 复制标题/描述/颜色/优先级/可见性/负责人+协助/标签/`loop`；时间窗按周期平移；**不**复制附件、子任务、富文本历史、dialog |
| 跳过 | 项目已删或已归档；子任务完成；未启用 `loop` |

## API

- `POST /api/project/task/add` · `loop?`
- `POST /api/project/task/update` · `loop?`
- 响应 `TaskView` 含 `loop` / `loopAt`

## 不做（本切片）

- 取消完成时回滚已生成实例
- 定时扫 `loop_at` 预创建
- 修改周期回填历史实例
