# OnlyOffice 集成

文件模块通过 `bluedock.office.*` 对接 OnlyOffice Document Server（微应用 id=`office`）。

## 配置（`application.yml` / 环境变量）

| 项 | 环境变量 | 说明 |
| -- | -------- | ---- |
| `enabled` | `BLUEDOCK_OFFICE_ENABLED` | 生产建议显式开启 |
| `allow-dev-token` | `BLUEDOCK_OFFICE_ALLOW_DEV_TOKEN` | 开发默认 `true`，未配 Document Server 也可签发会话 |
| `document-server-url` | `BLUEDOCK_OFFICE_DOCUMENT_SERVER_URL` | **浏览器可达**的 Document Server 基址 |
| `jwt-secret` | `BLUEDOCK_OFFICE_JWT_SECRET` | 非空则签发 HS256 JWT（须与 Document Server `JWT_SECRET` 一致） |
| `public-base-url` | `BLUEDOCK_OFFICE_PUBLIC_BASE_URL` | **Document Server 容器可达**的本服务基址（拉文档与回调） |
| `token-ttl-seconds` | `BLUEDOCK_OFFICE_TOKEN_TTL_SECONDS` | Redis 会话 TTL，默认 7200 |

## 本地 Compose（可选 profile）

```bash
# 开发依赖栈
docker compose -f deploy/docker-compose.dev.yml --profile onlyoffice up -d

# 联调镜像栈
docker compose -f deploy/docker-compose.yml --env-file deploy/.env.dev --profile onlyoffice up -d
```

| 项 | 值 |
| -- | -- |
| 镜像 | `onlyoffice/documentserver:8.2.2` |
| 宿主机 | `http://localhost:18082` |
| 默认 JWT | `bluedock_office_dev_secret`（与 `.env.dev.example` 注释一致） |

### 宿主机 JVM（`make dev-up` + `--profile onlyoffice`）

```bash
export BLUEDOCK_OFFICE_ENABLED=true
export BLUEDOCK_OFFICE_DOCUMENT_SERVER_URL=http://127.0.0.1:18082
export BLUEDOCK_OFFICE_JWT_SECRET=bluedock_office_dev_secret
# DS 容器经 host.docker.internal 访问 Nginx 入口
export BLUEDOCK_OFFICE_PUBLIC_BASE_URL=http://host.docker.internal:18080
```

### 联调镜像栈

```bash
BLUEDOCK_OFFICE_ENABLED=true
BLUEDOCK_OFFICE_DOCUMENT_SERVER_URL=http://localhost:18082
BLUEDOCK_OFFICE_JWT_SECRET=bluedock_office_dev_secret
BLUEDOCK_OFFICE_PUBLIC_BASE_URL=http://nginx
```

生产：外置 Document Server，见 `deploy/.env.prod.example` 的 `BLUEDOCK_OFFICE_*`；`docker-compose.prod.yml` 不含 OnlyOffice。

## API

| 路径 | 说明 |
| ---- | ---- |
| `GET /api/file/office/token?id=&mode=` | 签发编辑/预览会话（需登录；类型限 word/excel/ppt） |
| `GET/POST /api/file/content/office` | `action=download&token=` 拉二进制；`token`+`status`/`url` 回调回写（匿名，鉴权靠 Redis token）；或登录态 `id`+`url` |

回调成功响应：`{"error":0}`（OnlyOffice 约定）。

## Redis

`bluedock:file:office:{token}` → `{fileId,userId,mode,documentKey}`。

相关：[`docs/modules/file/api.md`](../modules/file/api.md) · [`docs/data/redis.md`](../data/redis.md) · [`docs/ops/deployment.md`](../ops/deployment.md)。
