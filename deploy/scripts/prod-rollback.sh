#!/usr/bin/env bash
# 回滚镜像版本（上一版、指定 TAG、或 K8s rollout undo）。
# 用法:
#   bash deploy/scripts/prod-rollback.sh                 # 上一版
#   bash deploy/scripts/prod-rollback.sh 1.0.0-old         # 指定 TAG
#   bash deploy/scripts/prod-rollback.sh --list            # 查看历史
#   bash deploy/scripts/prod-rollback.sh --target k8s      # K8s rollout undo
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STATE_FILE="${BLUEDOCK_DEPLOY_STATE:-$ROOT/deploy/.deploy-state}"
HISTORY_FILE="${BLUEDOCK_DEPLOY_HISTORY:-$ROOT/deploy/.deploy-history}"

TARGET="${BLUEDOCK_DEPLOY_TARGET:-compose}"
NAMESPACE="${BLUEDOCK_K8S_NAMESPACE:-bluedock-prod}"
TAG=""
LIST=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      TARGET="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --list)
      LIST=true
      shift
      ;;
    --*)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
    *)
      TAG="$1"
      shift
      ;;
  esac
done

list_history() {
  if [[ ! -f "$HISTORY_FILE" ]]; then
    echo "No deploy history yet."
    return
  fi
  local current=""
  [[ -f "$STATE_FILE" ]] && current="$(grep '^current=' "$STATE_FILE" | cut -d= -f2- || true)"
  echo "Deploy history (oldest → newest):"
  while IFS='|' read -r hist_tag hist_at; do
    local mark=" "
    [[ "$hist_tag" == "$current" ]] && mark="*"
    echo "  ${mark} ${hist_tag}  (${hist_at})"
  done <"$HISTORY_FILE"
  echo ""
  echo "* = current. Rollback: bash deploy/scripts/prod-rollback.sh <TAG>"
}

[[ "$LIST" == true ]] && list_history && exit 0

rollback_compose() {
  local rollback_tag="$TAG"
  if [[ -z "$rollback_tag" ]]; then
    [[ -f "$STATE_FILE" ]] || { echo "No $STATE_FILE — pass TAG or use --list." >&2; exit 1; }
    rollback_tag="$(grep '^previous=' "$STATE_FILE" | cut -d= -f2-)"
    [[ -n "$rollback_tag" ]] || { echo "No previous tag — pass TAG explicitly or see --list." >&2; exit 1; }
  fi
  echo "==> Rollback compose to ${rollback_tag}"
  bash "$ROOT/deploy/scripts/prod-switch.sh" "$rollback_tag" --target compose
}

rollback_k8s() {
  if [[ -n "$TAG" ]]; then
    bash "$ROOT/deploy/scripts/prod-switch.sh" "$TAG" --target k8s --namespace "$NAMESPACE"
    return
  fi
  if [[ ! -d "$ROOT/deploy/k8s" ]]; then
    echo "prod-rollback: deploy/k8s 尚未落地" >&2
    exit 1
  fi
  echo "==> kubectl rollout undo (namespace=${NAMESPACE})"
  kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-boot
  kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-worker-notify
  kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-worker-index
  kubectl -n "$NAMESPACE" rollout status deployment/bluedock-boot --timeout=300s
  kubectl -n "$NAMESPACE" rollout status deployment/bluedock-worker-notify --timeout=300s
  kubectl -n "$NAMESPACE" rollout status deployment/bluedock-worker-index --timeout=300s
}

case "$TARGET" in
  compose) rollback_compose ;;
  k8s) rollback_k8s ;;
  *)
    echo "Unknown --target: $TARGET" >&2
    exit 1
    ;;
esac

echo "Rollback complete"
