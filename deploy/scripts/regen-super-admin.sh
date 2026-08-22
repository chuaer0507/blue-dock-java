#!/usr/bin/env bash
# 本地重建超级管理员：删 id=1 → 重启 boot → bootstrap 写入 deploy/.env.dev
# 用法: bash deploy/scripts/regen-super-admin.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> delete bluedock_users.id=1"
docker compose -f deploy/docker-compose.dev.yml exec -T mysql \
  mysql -ubluedock -pbluedock_dev bluedock \
  -e "DELETE FROM bluedock_users WHERE id=1;"

echo "==> restart boot + workers"
bash deploy/scripts/dev-apps.sh --restart

echo "==> wait for health"
for i in $(seq 1 60); do
  if curl -sf --noproxy '*' http://127.0.0.1:18080/healthz >/dev/null 2>&1 \
    || curl -sf --noproxy '*' http://127.0.0.1:8080/healthz >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "==> credentials in deploy/.env.dev"
grep '#admin' deploy/.env.dev || {
  echo "WARN: #admin lines missing; bootstrap may not have run (check boot logs)" >&2
  exit 1
}

echo "==> smoke"
BASE_URL="${BASE_URL:-http://127.0.0.1:18080}" bash deploy/scripts/staging-core-smoke.sh
