#!/usr/bin/env bash
# 启动开发依赖（MySQL/Redis/Kafka/Nginx），不构建应用镜像。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/deploy/docker-compose.dev.yml")

if [[ -f "$ROOT/deploy/docker-compose.yml" ]]; then
  echo "==> stopping image-stack compose if running (port conflict)…"
  docker compose -f "$ROOT/deploy/docker-compose.yml" down >/dev/null 2>&1 || true
fi

echo "==> starting bluedock-dev dependencies…"
"${COMPOSE[@]}" up -d

echo "==> waiting for mysql…"
for _ in $(seq 1 60); do
  if "${COMPOSE[@]}" exec -T mysql mysqladmin ping -h localhost -uroot -pbluedock_root_dev --silent 2>/dev/null; then
    break
  fi
  sleep 2
done

cat <<EOF

Dev dependencies are up (project: bluedock-dev).

  MySQL  localhost:13306  user=bluedock  pass=bluedock_dev  db=bluedock
  Redis  localhost:16379  pass=bluedock_redis_dev
  Kafka  localhost:19092
  Nginx  http://localhost:18080  （对外唯一入口；boot :8080 仅供本容器回源）

Run apps on the host:

  make run-boot
  # 或：bash deploy/scripts/dev-apps.sh

Stop: docker compose -f deploy/docker-compose.dev.yml down
EOF
