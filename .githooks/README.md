# Git hooks（本仓库）

通过 `core.hooksPath` 指向本目录，不污染全局 hooks。

| Hook | 作用 |
| ---- | ---- |
| `commit-msg` | 校验首行符合 [PR / commit 约定](../.github/pull_request_title_conventions.md) |

## 启用（每个克隆执行一次）

```bash
bash scripts/setup_githooks.sh
# 等价于: git config core.hooksPath .githooks
```

## 临时跳过

```bash
git commit --no-verify -m "..."
```

仅在紧急热修时使用；合并进 `main` 的 PR 标题仍受 CI `ci-check-pr-title` 约束。
