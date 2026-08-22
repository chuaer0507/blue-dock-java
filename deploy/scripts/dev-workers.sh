#!/usr/bin/env bash
# 宿主机后台启动两个 Worker（notify + index）。日志：deploy/logs/*.log
# 依赖：已执行 make dev-up；另开终端跑 make run-boot / bash deploy/scripts/dev-boot.sh
# 或直接用：bash deploy/scripts/dev-apps.sh（boot + workers）
#
# 注意：不可用 pid="$(nohup ... &)" 取 PID——命令替换子 shell 退出会带走后台进程。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

LOG_DIR="${BLUEDOCK_DEV_LOG_DIR:-$ROOT/deploy/logs}"
mkdir -p "$LOG_DIR"

if [[ ! -f "$ROOT/pom.xml" ]]; then
  echo "dev-workers: 尚无 pom.xml" >&2
  exit 1
fi

start_worker() {
  local module="$1"
  local main_hint="$2"
  local log="$LOG_DIR/${module}.log"
  local pidfile="$LOG_DIR/${module}.pid"

  if [[ -f "$pidfile" ]]; then
    local old
    old="$(cat "$pidfile" 2>/dev/null || true)"
    if [[ -n "${old:-}" ]] && kill -0 "$old" 2>/dev/null; then
      echo "$module already running (pid=$old). log=$log"
      return 0
    fi
  fi

  if pgrep -fl "$main_hint" >/dev/null 2>&1; then
    echo "$module already running (matched process)."
    return 0
  fi

  echo "==> starting $module → $log"
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

# 若 Java 主进程已起来，把 pidfile 改成 Java pid（避免停机只杀到 setsid/maven）
bind_java_pid() {
  local module="$1"
  local hint="$2"
  local pidfile="$LOG_DIR/${module}.pid"
  local jp
  jp="$(pgrep -f "$hint" 2>/dev/null | head -n 1 || true)"
  if [[ -n "${jp:-}" ]]; then
    echo "$jp" >"$pidfile"
    echo "  java_pid=$jp ($module)"
  fi
}

start_worker bluedock-worker-notify NotifyWorkerApplication
start_worker bluedock-worker-index IndexWorkerApplication

# 给 Spring Boot 一点启动时间再绑 pid
sleep 3
bind_java_pid bluedock-worker-notify NotifyWorkerApplication
bind_java_pid bluedock-worker-index IndexWorkerApplication

echo ""
echo "Workers launching. Tail logs:"
echo "  tail -f $LOG_DIR/bluedock-worker-notify.log"
echo "  tail -f $LOG_DIR/bluedock-worker-index.log"
echo "Stop:"
echo "  kill \$(cat $LOG_DIR/bluedock-worker-notify.pid $LOG_DIR/bluedock-worker-index.pid 2>/dev/null)"
