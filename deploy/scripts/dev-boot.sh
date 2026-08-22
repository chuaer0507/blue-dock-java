#!/usr/bin/env bash
# 宿主机启动 bluedock-boot；若 :8080 已健康则跳过。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

HEALTH_URL="${BLUEDOCK_BOOT_HEALTH_URL:-http://localhost:8080/actuator/health}"
CURL="${CURL:-curl --noproxy '*' -sf --max-time 3}"

if [[ ! -f "$ROOT/pom.xml" ]]; then
  echo "dev-boot: 尚无 pom.xml" >&2
  exit 1
fi

if $CURL "$HEALTH_URL" >/dev/null 2>&1; then
  echo "bluedock-boot already running (${HEALTH_URL} → UP)."
  echo "  对外入口: http://localhost:18080/"
  exit 0
fi

if lsof -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ERROR: port 8080 is in use but ${HEALTH_URL} is not healthy." >&2
  lsof -iTCP:8080 -sTCP:LISTEN >&2 || true
  exit 1
fi

echo "==> starting bluedock-boot (mvn spring-boot:run) ..."
exec mvn -pl bluedock-boot -am spring-boot:run "$@"
