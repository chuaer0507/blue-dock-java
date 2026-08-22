# PR Title Convention

PR 标题遵循 [Angular Commit Message Convention](https://github.com/angular/angular/blob/master/CONTRIBUTING.md#commit)，便于阅读历史与后续自动化 changelog。

```text
<type>(<scope>): <summary>
  │       │          │
  │       │          └─⫸ Summary：祈使语气；英文可小写开头；中文直接写；句末无句号
  │       │
  │       └─⫸ Scope（可选）：见下方列表
  │
  └─⫸ Type：见下方表格
```

CI 工作流 [`ci-check-pr-title.yml`](workflows/ci-check-pr-title.yml) 会校验 PR 标题格式。

## Type

| type | 说明 | 建议进 changelog |
| ---- | ---- | ---------------- |
| `feat` | 新功能 | ✅ |
| `fix` | Bug 修复 | ✅ |
| `perf` | 性能优化 | ✅ |
| `test` | 测试增补 / 修正 | ❌ |
| `docs` | 仅文档 | ❌ |
| `refactor` | 行为不变的重构 | ❌ |
| `build` | 构建系统 / 依赖 | ❌ |
| `ci` | CI 配置与脚本 | ❌ |
| `chore` | 杂项维护 | ❌ |

破坏性变更：在 PR 描述 footer 写 `BREAKING CHANGE:` 说明迁移方式。

跳过 changelog（若启用自动发版说明）：标题加 `(no-changelog)` 后缀。

## Scope（可选）

与本仓库模块对应；改动跨多处时可省略 scope。

| scope | 范围 |
| ----- | ---- |
| `auth` / `user` / `org` | 账号、用户、部门权限 |
| `project` / `task` / `messenger` / `file` | 核心协作域 |
| `report` / `system` / `search` / `assistant` | 周边域 |
| `realtime` / `common` | 实时 / 公共 |
| `boot` / `worker` | 可执行 JAR（boot / notify·index Worker） |
| `deploy` | Compose / K8s / smoke 脚本 |
| `deps` | 依赖升级 |

## 示例

```text
feat(project): 新增项目成员邀请
fix(messenger): 修复未读计数
ci: 拆分 PR 与 main 编排
docs(deploy): 补充 GHCR 镜像切换说明
refactor(common): 收敛 ResultModel
chore(deps): 升级 Spring Boot
```

## 与 commit message

本地 commit **同一套** type/scope：由 [`.githooks/commit-msg`](../.githooks/commit-msg) 在 `git commit` 时校验首行。

```bash
bash scripts/setup_githooks.sh   # 每个克隆执行一次
```

合并可用 squash，以 **PR 标题** 为准写入 `main`；PR 标题另由 CI [`ci-check-pr-title.yml`](workflows/ci-check-pr-title.yml) 校验。紧急跳过本地钩子：`git commit --no-verify`（仍建议改合规后再推）。
