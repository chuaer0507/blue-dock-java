# GitHub Actions / Workflows

本目录存放 CI/CD 配置。完整参考以本文为准；根目录 [README.md](../README.md) / [CLAUDE.md](../CLAUDE.md) 有摘要；部署见 [docs/ops/deployment.md](../docs/ops/deployment.md)。

目录与前缀约定：编排按触发拆分；reusable 以 `*-reusable.yml` 结尾；`release-*` 由 `ci-main` 在 Tag 时调用。

---

## 目录结构

```
.github/
├── WORKFLOWS.md
├── CLAUDE.md
├── actionlint.yml
├── pull_request_title_conventions.md
├── actions/
│   └── setup-java/                       # Temurin JDK 25 + Maven cache
└── workflows/
    ├── ci-pull-requests.yml
    ├── ci-main.yml
    ├── ci-check-pr-title.yml
    ├── ci-check-reusable.yml             # mvn test + package boot/workers
    ├── ci-k8s-manifest-reusable.yml      # k8s-manifest-check.sh
    ├── ci-compose-smoke-reusable.yml     # sync-env-tag + compose + smoke
    └── release-images-reusable.yml       # 推送 GHCR（Tag / 手动）
```

仓库根另有：

```
.githooks/commit-msg
scripts/setup_githooks.sh
```

相关脚本：

| 脚本 | CI 用途 |
| ---- | ------- |
| [`deploy/scripts/k8s-manifest-check.sh`](../deploy/scripts/k8s-manifest-check.sh) | K8s 预检 |
| [`deploy/scripts/sync-env-image-tag.sh`](../deploy/scripts/sync-env-image-tag.sh) | git tag → `.env.dev` / `.env.prod` |
| [`deploy/scripts/staging-core-smoke.sh`](../deploy/scripts/staging-core-smoke.sh) | 核心 API smoke |
| [`deploy/scripts/image-build.sh`](../deploy/scripts/image-build.sh) / `image-push.sh` / `prod-switch.sh` | 本地发版对照 |

---

## 前缀分类

| 前缀 | 用途 |
| ---- | ---- |
| `ci-` | 编排 / 质量门禁 |
| `release-` | 发版（reusable，由 `ci-main` 调用） |
| `*-reusable.yml` | 仅 `workflow_call` |

---

## 架构概览

```
┌────────────────────────────────────────────────────────────────────┐
│                       BlueDock CI/CD                              │
├────────────────────────────────────────────────────────────────────┤
│  PR→main     → ci-pull-requests → check → k8s + compose-smoke      │
│  push main   → ci-main          → check → k8s + compose-smoke      │
│  Tag v* / 手动 → ci-main        → check → (+k8s/smoke*) + GHCR     │
│  + ci-check-pr-title（PR 标题）                                      │
└────────────────────────────────────────────────────────────────────┘
* workflow_dispatch 仅跑 check + release-images（跳过 k8s / compose）。
```

---

## 触发条件

| 文件 | 事件 | 行为 |
| ---- | ---- | ---- |
| `ci-pull-requests.yml` | PR → `main` | `check` → 并行 `k8s` + `compose-smoke` |
| `ci-main.yml` | push → `main` | 同上 |
| `ci-main.yml` | Tag `v*` | 上述 + `release-images` |
| `ci-main.yml` | 手动 + `tag` | `check` → `release-images` |
| `ci-check-pr-title.yml` | PR 标题事件 | Angular type 校验 |

JDK：`25`，与 [technology-stack.md](../docs/architecture/technology-stack.md)、`actions/setup-java` 同步。

镜像：`ghcr.io/<owner>/bluedock-boot|bluedock-worker-notify|bluedock-worker-index:<tag>`。

版本：`BLUEDOCK_VERSION`（git tag，如 `v1.0.0`）写在 `deploy/.env.*`，镜像 tag 同值（见 `sync-env-image-tag.sh`）。

---

## Workflow 清单

### `ci-` 编排 / 检查

| 文件 | 触发 | 说明 |
| ---- | ---- | ---- |
| `ci-pull-requests.yml` | PR → main | 编排 + concurrency |
| `ci-main.yml` | push main / Tag / 手动 | 编排；Tag 或手动再跑 release |
| `ci-check-pr-title.yml` | PR 标题 | Angular type |
| `ci-check-reusable.yml` | `workflow_call` | `mvn clean test` → package boot + workers |
| `ci-k8s-manifest-reusable.yml` | `workflow_call` | `k8s-manifest-check.sh` |
| `ci-compose-smoke-reusable.yml` | `workflow_call` | `sync-env-tag` → compose `--env-file .env.dev` → healthz → smoke |

### `release-` 发版

| 文件 | 条件 | 说明 |
| ---- | ---- | ---- |
| `release-images-reusable.yml` | Tag / 手动 | Buildx 推送三镜像到 GHCR |

---

## `ci-check` 步骤

1. `actions/checkout@v4`
2. `./.github/actions/setup-java`（JDK 25）
3. `mvn -B clean test`
4. `mvn -B -pl bluedock-boot -am package -DskipTests`
5. `mvn -B -pl bluedock-worker-notify,bluedock-worker-index -am package -DskipTests`

## `ci-compose-smoke` 步骤

1. `checkout`（`fetch-depth: 0`，供 `git describe`）
2. `bash deploy/scripts/sync-env-image-tag.sh`
3. `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.dev up -d --build`
4. 等待 `http://localhost/healthz`
5. `BASE_URL=http://localhost bash deploy/scripts/staging-core-smoke.sh`
6. 失败打日志；`always` 时 `down -v`

---

## Secrets / Permissions

| 类别 | 名称 | 用途 |
| ---- | ---- | ---- |
| Token | `GITHUB_TOKEN` | `release-images` 推送 GHCR（`packages: write`） |

默认 `permissions: contents: read`；`release-images` job 另授 `packages: write`。

---

## 典型流程

### PR → main

```
ci-pull-requests
  └─ check
       ├─ k8s-manifest
       └─ compose-smoke
+ ci-check-pr-title
```

### 推送 `v1.2.3`

```
ci-main
  └─ check
       ├─ k8s-manifest
       ├─ compose-smoke
       └─ release-images → ghcr.io/<owner>/bluedock-*:v1.2.3
```

生产切换：

```bash
# Compose（镜像名写死为 bluedock-boot 等）
bash deploy/scripts/prod-switch.sh v1.2.3
# K8s
BLUEDOCK_REGISTRY=ghcr.io/<owner> bash deploy/scripts/prod-switch.sh v1.2.3 --target k8s
```

---

## 本地对照

```bash
make test
make package
make package-workers
make k8s-check
make sync-env-tag
make compose-up
BASE_URL=http://localhost make smoke
```

---

## 维护注意

- 新 workflow 须带前缀：`ci-` / `release-`
- 可复用逻辑放 `*-reusable.yml`；**不要**再给 release 单独挂第二套 Tag 触发器
- JDK Setup 统一走 `setup-java`
- 改 JDK：同步 `ci-pull-requests.yml`、`ci-main.yml`、`setup-java` 默认值、技术栈文档
- 调用 reusable 时用 `secrets: inherit`；`with` 勿依赖 caller 的 `env` context
- Compose / 镜像 / env tag 变更须同步本文件与 [docs/ops/deployment.md](../docs/ops/deployment.md)
