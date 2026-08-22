#!/usr/bin/env bash
# 宿主机后台启动 bluedock-boot + 2 Worker。
# 前置：make dev-up
# 用法：
#   bash deploy/scripts/dev-apps.sh           # 健康则跳过
#   bash deploy/scripts/dev-apps.sh --restart # 先停再建
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

LOG_DIR="${BLUEDOCK_DEV_LOG_DIR:-$ROOT/deploy/logs}"
mkdir -p "$LOG_DIR"

APPS=(bluedock-boot bluedock-worker-notify bluedock-worker-index)
BOOT_HEALTH_URL="${BLUEDOCK_BOOT_HEALTH_URL:-http://localhost:8080/actuator/health}"
CURL=(curl --noproxy '*' -sf --max-time 2)
BOOT_WAIT_SECS="${BLUEDOCK_BOOT_WAIT_SECS:-90}"

# module → 主类片段（用于 pid 解析 / 停进程；pidfile 优先记 Java 主进程）
main_hint() {
  case "$1" in
    bluedock-boot) echo 'com.bluedock.boot.BlueDockBootApplication' ;;
    bluedock-worker-notify) echo 'com.bluedock.worker.notify.NotifyWorkerApplication' ;;
    bluedock-worker-index) echo 'com.bluedock.worker.index.IndexWorkerApplication' ;;
    *) echo "" ;;
  esac
}

FORCE=0
for arg in "$@"; do
  case "$arg" in
    -r | --restart | -f | --force) FORCE=1 ;;
    -h | --help)
      cat <<'EOF'
宿主机后台启动 bluedock-boot + 2 Worker。前置：make dev-up

用法：
  bash deploy/scripts/dev-apps.sh           # 健康则跳过
  bash deploy/scripts/dev-apps.sh --restart # 先停再建（改代码后推荐）
EOF
      exit 0
      ;;
    *)
      echo "Unknown arg: $arg (try --help)" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "$ROOT/pom.xml" ]]; then
  echo "dev-apps: 尚无 pom.xml" >&2
  exit 1
fi

boot_healthy() {
  "${CURL[@]}" "$BOOT_HEALTH_URL" >/dev/null 2>&1
}

pid_dead_or_zombie() {
  local pid="$1"
  if [[ -z "${pid:-}" ]]; then
    return 0
  fi
  if ! kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  local st
  st="$(ps -o state= -p "$pid" 2>/dev/null | tr -d '[:space:]' || true)"
  case "$st" in
    Z*) return 0 ;;
  esac
  return 1
}

java_pids_for() {
  local hint
  hint="$(main_hint "$1")"
  if [[ -z "$hint" ]]; then
    return 0
  fi
  pgrep -f "$hint" 2>/dev/null || true
}

# 将 pidfile 写成 Java 主进程（若已起来）；否则保留 launcher pid
refresh_pidfile() {
  local name="$1"
  local pidfile="$LOG_DIR/${name}.pid"
  local jp
  jp="$(java_pids_for "$name" | head -n 1 || true)"
  if [[ -n "${jp:-}" ]]; then
    echo "$jp" >"$pidfile"
  fi
}

stop_one() {
  local name="$1"
  local pidfile="$LOG_DIR/${name}.pid"
  local old=""
  local jp

  if [[ -f "$pidfile" ]]; then
    old="$(cat "$pidfile" 2>/dev/null || true)"
    rm -f "$pidfile"
  fi

  if [[ -n "${old:-}" ]] && ! pid_dead_or_zombie "$old"; then
    echo "==> stopping $name (pid=$old)"
    kill "$old" 2>/dev/null || true
    sleep 1
    kill -9 "$old" 2>/dev/null || true
  fi

  # 清掉仍存活的 Java 主进程（pidfile 曾指向 setsid/maven 时常见）
  while read -r jp; do
    [[ -z "${jp:-}" ]] && continue
    echo "==> stopping $name java (pid=$jp)"
    kill "$jp" 2>/dev/null || true
    sleep 1
    kill -9 "$jp" 2>/dev/null || true
  done < <(java_pids_for "$name")

  if [[ "$name" == "bluedock-boot" ]] && lsof -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
    kill "$(lsof -t -iTCP:8080 -sTCP:LISTEN)" 2>/dev/null || true
    sleep 1
  fi
}

start_one() {
  local module="$1"
  local log="$LOG_DIR/${module}.log"
  local pidfile="$LOG_DIR/${module}.pid"

  echo "==> starting $module → $log"
  : >"$log"
  if command -v setsid >/dev/null 2>&1; then
    setsid bash -c "cd \"$ROOT\" && exec mvn -pl \"$module\" -am spring-boot:run" \
      >"$log" 2>&1 </dev/null &
  else
    nohup bash -c "cd \"$ROOT\" && exec mvn -pl \"$module\" -am spring-boot:run" \
      >"$log" 2>&1 </dev/null &
  fi
  echo $! >"$pidfile"
  disown $! 2>/dev/null || true
  echo "  launcher_pid=$(cat "$pidfile")"
}

wait_boot_ready() {
  local i
  echo "==> waiting for bluedock-boot (≤${BOOT_WAIT_SECS}s)…"
  for ((i = 1; i <= BOOT_WAIT_SECS; i++)); do
    if boot_healthy; then
      refresh_pidfile bluedock-boot
      echo "  bluedock-boot ready (pid=$(cat "$LOG_DIR/bluedock-boot.pid" 2>/dev/null || echo '?'))"
      return 0
    fi
    sleep 1
  done
  echo "dev-apps: bluedock-boot 未在 ${BOOT_WAIT_SECS}s 内就绪，见 $LOG_DIR/bluedock-boot.log" >&2
  return 1
}

worker_running() {
  local module="$1"
  local hint="$2"
  local pidfile="$LOG_DIR/${module}.pid"
  if [[ -f "$pidfile" ]]; then
    local old
    old="$(cat "$pidfile" 2>/dev/null || true)"
    if [[ -n "${old:-}" ]] && ! pid_dead_or_zombie "$old"; then
      return 0
    fi
  fi
  pgrep -fl "$hint" >/dev/null 2>&1
}

if [[ "$FORCE" -eq 1 ]]; then
  for app in "${APPS[@]}"; do
    stop_one "$app"
  done
elif boot_healthy && worker_running bluedock-worker-notify NotifyWorkerApplication \
  && worker_running bluedock-worker-index IndexWorkerApplication; then
  echo "bluedock-boot + workers already healthy; skip. Use --restart to rebuild."
  echo "  对外入口: http://localhost:18080/"
  exit 0
fi

if ! boot_healthy; then
  start_one bluedock-boot
  wait_boot_ready
else
  echo "bluedock-boot already healthy; skip boot."
  refresh_pidfile bluedock-boot
fi

bash "$ROOT/deploy/scripts/dev-workers.sh"
refresh_pidfile bluedock-worker-notify
refresh_pidfile bluedock-worker-index

echo ""
echo "Ready. Logs:"
echo "  tail -f $LOG_DIR/bluedock-boot.log"
echo "  tail -f $LOG_DIR/bluedock-worker-notify.log"
echo "  tail -f $LOG_DIR/bluedock-worker-index.log"
echo "对外入口: http://localhost:18080/"
echo "冒烟: BASE_URL=http://localhost:18080 make smoke"
