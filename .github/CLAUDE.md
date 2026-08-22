## .github Quick Reference

本目录存放 BlueDock 的 GitHub Actions 基础设施。完整说明见 [WORKFLOWS.md](WORKFLOWS.md)；部署见 [docs/ops/deployment.md](../docs/ops/deployment.md)。

### Key Files

| File/Folder | Purpose |
|-------------|---------|
| `WORKFLOWS.md` | CI/CD 完整文档 |
| `actionlint.yml` | Workflow linter 配置 |
| `pull_request_title_conventions.md` | PR 标题约定 |
| `workflows/` | GitHub Actions workflows |
| `actions/` | Reusable composite actions |

### Workflow Naming

| Prefix | Purpose |
|--------|---------|
| `ci-` | 编排 / 质量门禁 |
| `release-` | 发版（reusable，由 `ci-main` 在 Tag 时调用） |

Reusable：`-reusable` 后缀；仅 `workflow_call`。

### What runs when

| Event | Workflow |
|-------|----------|
| PR → `main` | `ci-pull-requests.yml` → check → k8s + compose-smoke；另跑 `ci-check-pr-title` |
| push → `main` | `ci-main.yml` → check → k8s + compose-smoke |
| Tag `v*` / 手动 | `ci-main.yml` → check（+ k8s/smoke*）+ `release-images` → GHCR |

Compose smoke：`sync-env-image-tag` → `docker-compose.yml --env-file .env.dev` → `staging-core-smoke`。

镜像：`ghcr.io/<owner>/bluedock-boot|bluedock-worker-notify|bluedock-worker-index:<tag>`。

本地 commit 钩子：仓库根 `.githooks/commit-msg`（`bash scripts/setup_githooks.sh`）。
