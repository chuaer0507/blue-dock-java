# 部署

## 本地开发依赖（Compose）

```bash
make dev-up          # MySQL / Redis / Kafka / Nginx
make run-boot        # 或 make dev / bash deploy/scripts/dev-apps.sh
```

| 服务 | 镜像 | 宿主机 |
| ---- | ---- | ------ |
| MySQL | `mysql:9.7.2` | `localhost:13306` · db=`bluedock` · user=`task` / `bluedock_dev` |
| Redis | `redis:8.2.8` | `localhost:16379` · pass=`bluedock_redis_dev` |
| Kafka | `apache/kafka:4.3.1` KRaft | `localhost:19092` |
| Nginx | `nginx:1.30.4` | **`http://localhost:18080`**（对外唯一 HTTP 入口） |
| OpenSearch（可选） | `opensearchproject/opensearch:2.19.1` | `localhost:19200` · profile=`opensearch` |
| OnlyOffice（可选） | `onlyoffice/documentserver:8.2.2` | `localhost:18082` · profile=`onlyoffice` |
| bluedock-boot（宿主机） | — | **不映射宿主机对外端口**；仅 `127.0.0.1:8080` 供 Nginx（`host.docker.internal`）回源 |
| Workers（宿主机） | — | `bluedock-worker-notify` / `bluedock-worker-index`（`make dev` 后台启） |

> Compose 暴露端口见上表。客户端 / smoke 只走 Nginx，勿把 boot `:8080` 当入口。  
> OpenSearch（可选 profile，默认不启）：
> - 开发依赖：`docker compose -f deploy/docker-compose.dev.yml --profile opensearch up -d`（宿主机 `localhost:19200`）
> - 联调镜像：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env.dev --profile opensearch up -d`（容器内 `http://opensearch:9200`，不映射宿主机端口）
> - 启用检索：`BLUEDOCK_SEARCH_OPENSEARCH_ENABLED=true` · `BLUEDOCK_SEARCH_ENGINE=opensearch`（联调栈 URL 默认已指向 `http://opensearch:9200`）
> - 生产：中间件外置，见 `.env.prod.example` 的 `BLUEDOCK_SEARCH_*`；`docker-compose.prod.yml` 不含 OpenSearch  
> OnlyOffice（可选 profile，默认不启）：
> - 开发依赖：`docker compose -f deploy/docker-compose.dev.yml --profile onlyoffice up -d`（宿主机 `localhost:18082`）
> - 联调镜像：`--profile onlyoffice`；浏览器 `BLUEDOCK_OFFICE_DOCUMENT_SERVER_URL=http://localhost:18082`，DS 回源 `BLUEDOCK_OFFICE_PUBLIC_BASE_URL=http://nginx`
> - 环境变量见 `.env.dev.example` / [office-onlyoffice.md](../infra/office-onlyoffice.md)；生产外置 DS

Compose 文件：[`deploy/docker-compose.dev.yml`](../../deploy/docker-compose.dev.yml) · [`deploy/docker-compose.yml`](../../deploy/docker-compose.yml)。

应用配置见 `bluedock-boot/src/main/resources/application.yml`（默认连上述本地地址）。

### 超级管理员 bootstrap

库中无 `bluedock_users.id=1` 时，首次启动自动生成超管（**不依赖** `bluedock.seed.enabled`）：

- 邮箱（登录用户名）：随机 `admin_<8位>@bluedock.local`
- 密码：随机 16 位（可直接登录，不强制改密）
- 凭据写入 `deploy/.env.dev`（或 prod profile → `.env.prod`）：`#admin账号：` / `#admin密码：`
- 路径可用 `BLUEDOCK_DEPLOY_ENV_FILE` 覆盖；已有 id=1 则跳过，不改写 env

联调 Compose 将 `deploy/` 挂到容器 `/app/deploy`，便于宿主机读取凭据。系统机器人等仍由 `BLUEDOCK_SEED_ENABLED` 控制。`make smoke` 在 `.env.dev` 含上述凭据时会额外校验超管登录。

本地若需**重新生成**超管（已有 id=1 时 bootstrap 会跳过）：

```bash
bash deploy/scripts/regen-super-admin.sh
```

登录：`GET|POST /api/users/login`（经 Nginx）。须先 `GET /api/users/key/client` 取公钥，再以 RSA-OAEP 密文传 `password`+`keyId`；失败达阈值后带验证码。见 [auth-wire.md](../modules/user-account/auth-wire.md)。改 V1 后本地库需重建（或 SNAPSHOT 启动时自动 `flyway.repair` 同步 checksum，见 `FlywayDevRepairConfig`）。

上传：分片见 [upload.md](../infra/upload.md)；管理端存储引擎见 [oss-settings.md](../infra/oss-settings.md)。

## 进程拓扑

```
客户端
    │ HTTPS / WSS
    ▼
Nginx（TLS · 静态 · /api · /ws 反代 · client_max_body_size；`/api/ai/invoke/stream/` 关 proxy_buffering 以支持 SSE）
    │
    ├─► bluedock-boot（REST + WS；虚拟线程）
    │       ├─ MySQL（强一致）
    │       ├─ Redis（会话 / 限流 / presence）
    │       └─ Kafka（生产者）
    │
    └─► Workers（无 HTTP）
            ├─ bluedock-worker-notify（通知 / 推送 / 邮件）
            └─ bluedock-worker-index（搜索索引）
```

多实例：boot 可水平扩展；WS 跨实例经 Kafka fanout（见 [realtime.md](../architecture/realtime.md)）。Worker 按消费者组扩容。

## 生产配置（环境变量）

生产机：

```bash
cp deploy/.env.prod.example deploy/.env.prod
# 编辑 deploy/.env.prod（DB / Redis / Kafka / BLUEDOCK_JWT_SECRET / BLUEDOCK_SEED_ENABLED=false …）
```

| 文件 | 用途 |
| ---- | ---- |
| [`.env.prod.example`](../../deploy/.env.prod.example) | 生产模板 → 复制为 `.env.prod`（Git 忽略） |
| [`.env.dev.example`](../../deploy/.env.dev.example) | 本地占位说明 |
| `application-docker.yml` | `SPRING_PROFILES_ACTIVE=docker` 时覆盖本机端口 / `0.0.0.0` |

关键变量（详见 `.env.prod.example`）：

| 变量 | 说明 |
| ---- | ---- |
| `BLUEDOCK_VERSION` | git tag（如 `v1.0.0`）；Compose 镜像 tag 同值（见 `sync-env-image-tag.sh`）；镜像名写死在 compose（`bluedock-boot` 等） |
| `SPRING_PROFILES_ACTIVE` | 生产 / 联调填 `docker` |
| `SPRING_DATASOURCE_*` | MySQL |
| `SPRING_DATA_REDIS_*` | Redis |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka |
| `BLUEDOCK_JWT_SECRET` | JWT（≥32 字符） |
| `BLUEDOCK_SEED_ENABLED` | 生产必须 `false` |
| `BLUEDOCK_DEMO_ACCOUNT` / `BLUEDOCK_DEMO_PASSWORD` | 可选；配置后匿名 `GET /api/system/demo` 回显演示帐号 |
| `BLUEDOCK_CHANGELOG_PATH` | 可选；更新日志文件路径（Docker 默认 `/app/CHANGELOG.md`） |
| `BLUEDOCK_UPLOAD_DIR` / `BLUEDOCK_LICENSE_PATH` | 容器内路径 |
| `BLUEDOCK_SEARCH_*` / `BLUEDOCK_MEETING_*` / `BLUEDOCK_OFFICE_*` / `BLUEDOCK_GEOIP_MMDB` / `BLUEDOCK_APPS_*` | 可选 |

联调栈在 `docker-compose.yml` 内联开发默认值；生产栈用 `docker-compose.prod.yml` + `env_file: .env.prod`（**不在服务器 build**）。

## 镜像与发布

| 产物 | Dockerfile | 镜像名（约定） |
| ---- | ---------- | -------------- |
| API | [`deploy/docker/Dockerfile.boot`](../../deploy/docker/Dockerfile.boot) | Compose / 本地：`bluedock-boot:${BLUEDOCK_VERSION}`；推送可选 `${BLUEDOCK_REGISTRY}/bluedock-boot:${BLUEDOCK_VERSION}` |
| 通知 Worker | [`Dockerfile.worker-notify`](../../deploy/docker/Dockerfile.worker-notify) | Compose：`bluedock-worker-notify:…`；推送：`${BLUEDOCK_REGISTRY}/…` |
| 索引 Worker | [`Dockerfile.worker-index`](../../deploy/docker/Dockerfile.worker-index) | 同上 |

联调栈：

```bash
make sync-env-tag                 # 按 git describe 写 .env.dev / .env.prod
make compose-up                   # --env-file deploy/.env.dev；镜像 tag = BLUEDOCK_VERSION
# 或：docker compose -f deploy/docker-compose.yml --env-file deploy/.env.dev up -d --build
curl -sf http://localhost/healthz
BASE_URL=http://localhost make smoke
```

版本字段：

| 变量 | 用途 |
| ---- | ---- |
| `BLUEDOCK_VERSION`（`.env`） | git tag（如 `v1.0.0`）；应用版本与 Compose 镜像 tag **共用** |
| `BLUEDOCK_REGISTRY`（仅 shell / Make） | 构建推送 / K8s `set image` 的仓库前缀；**不**写入 `.env`；Compose 镜像名写死为 `bluedock-boot` 等 |

写入时机（仅 `BLUEDOCK_VERSION`）：`make sync-env-tag` · `image-build` · `prod-switch` 成功后。

日常开发仍用 `make dev-up`（仅依赖）+ 宿主机 JVM，见上文。

发布：

```bash
cp deploy/.env.prod.example deploy/.env.prod   # 生产机一次
make release BLUEDOCK_REGISTRY=ghcr.io/<owner> TAG=v1.0.0
bash deploy/scripts/prod-switch.sh v1.0.0      # Compose：拉 bluedock-*:TAG
bash deploy/scripts/prod-rollback.sh           # 回滚上一 TAG
```

**当前状态**：Alpine Dockerfile + 联调 / 生产 Compose + `.env.*.example`（仅 `BLUEDOCK_VERSION`=git tag）+ **K8s Kustomize**（staging/prod）已落地。CI（check / k8s / compose-smoke / GHCR release）见 [`.github/WORKFLOWS.md`](../../.github/WORKFLOWS.md)。

## 生产拓扑选项

### A. Compose（小规模 / 单机）

1. 依赖：MySQL / Redis / Kafka（可托管；**不**放进 `docker-compose.prod.yml`）
2. 配置 `deploy/.env.prod`（从 example 复制；镜像名为 `bluedock-boot` 等，写死在 compose）
3. 跑 `bluedock-boot` + 两个 Worker + Nginx（TLS）
4. 切换：`bash deploy/scripts/prod-switch.sh <TAG>`

### B. Kubernetes

1. 预检：`make k8s-check` / `bash deploy/scripts/k8s-manifest-check.sh`
2. 创建 Secret：见 [`deploy/k8s/base/secret.yaml.example`](../../deploy/k8s/base/secret.yaml.example)
3. 部署：`kubectl apply -k deploy/k8s/overlays/staging`（或 `prod`）
4. 切换镜像：`BLUEDOCK_REGISTRY=… bash deploy/scripts/prod-switch.sh <TAG> --target k8s`
5. 说明：[`deploy/k8s/README.md`](../../deploy/k8s/README.md)

## 验收

| 步骤 | 命令 / 入口 |
| ---- | ----------- |
| 编译测试 | `make test` / CI `ci-check` |
| 本地依赖 | `make dev-up` |
| 手工冒烟 | [regression.md](regression.md)（经 Nginx `:18080`） |
| API smoke | `BASE_URL=http://localhost:18080 make smoke`（经 Nginx；`make smoke` 默认同址） |
