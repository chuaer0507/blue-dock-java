#!/usr/bin/env bash
# 生产切换镜像版本（仅拉取 + 重建容器，不在服务器编译）。
# 健康检查失败时自动回滚到切换前版本。
# 用法:
#   bash deploy/scripts/prod-switch.sh <TAG> [--target compose|k8s] [--namespace bluedock-prod] [--no-verify]
# Compose：镜像名写死在 docker-compose.prod.yml（bluedock-boot 等）；仅回写 .env.prod 的 BLUEDOCK_VERSION
# K8s：BLUEDOCK_REGISTRY 指定镜像前缀（默认 registry.example.com/bluedock）
#   deploy/.env.prod（compose 目标必填；从 .env.prod.example 复制）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEPLOY_DIR="$ROOT/deploy"
STATE_FILE="${BLUEDOCK_DEPLOY_STATE:-$DEPLOY_DIR/.deploy-state}"
HISTORY_FILE="${BLUEDOCK_DEPLOY_HISTORY:-$DEPLOY_DIR/.deploy-history}"

TARGET="${BLUEDOCK_DEPLOY_TARGET:-compose}"
NAMESPACE="${BLUEDOCK_K8S_NAMESPACE:-bluedock-prod}"
VERIFY=true

usage() {
  echo "Usage: $0 <TAG> [--target compose|k8s] [--namespace <ns>] [--no-verify]" >&2
  exit 1
}

[[ $# -ge 1 ]] || usage
TAG="$1"
shift

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
    --no-verify)
      VERIFY=false
      shift
      ;;
    *)
      usage
      ;;
  esac
done

REGISTRY="${BLUEDOCK_REGISTRY:-registry.example.com/bluedock}"
export BLUEDOCK_VERSION="$TAG"

PREV_TAG=""
if [[ -f "$STATE_FILE" ]]; then
  PREV_TAG="$(grep '^current=' "$STATE_FILE" | cut -d= -f2- || true)"
fi

wait_healthz() {
  local url="${BLUEDOCK_HEALTH_URL:-http://localhost/healthz}"
  local attempts="${BLUEDOCK_HEALTH_ATTEMPTS:-60}"
  local interval="${BLUEDOCK_HEALTH_INTERVAL_SEC:-5}"
  echo "==> Waiting for healthz: ${url} (max ${attempts}x${interval}s)"
  for _ in $(seq 1 "$attempts"); do
    if curl -sf --noproxy '*' "$url" >/dev/null 2>&1; then
      echo "Healthz OK"
      return 0
    fi
    sleep "$interval"
  done
  echo "ERROR: healthz not ready after ${attempts} attempts" >&2
  return 1
}

wait_readyz() {
  local url="${BLUEDOCK_READY_URL:-http://localhost/readyz}"
  local attempts="${BLUEDOCK_READY_ATTEMPTS:-30}"
  local interval="${BLUEDOCK_READY_INTERVAL_SEC:-5}"
  echo "==> Waiting for readyz: ${url} (max ${attempts}x${interval}s)"
  for _ in $(seq 1 "$attempts"); do
    if curl -sf --noproxy '*' "$url" >/dev/null 2>&1; then
      echo "Readyz OK"
      return 0
    fi
    sleep "$interval"
  done
  echo "ERROR: readyz not ready after ${attempts} attempts" >&2
  return 1
}

write_state() {
  local now
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname "$STATE_FILE")"
  {
    echo "previous=${PREV_TAG}"
    echo "current=${TAG}"
    echo "switched_at=${now}"
    echo "target=${TARGET}"
  } >"$STATE_FILE"
  if ! grep -q "^${TAG}|" "$HISTORY_FILE" 2>/dev/null; then
    echo "${TAG}|${now}" >>"$HISTORY_FILE"
  fi
}

compose_up() {
  local tag="$1"
  local env_file="${BLUEDOCK_ENV_FILE:-$DEPLOY_DIR/.env.prod}"
  export BLUEDOCK_VERSION="$tag"
  docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml" --env-file "$env_file" pull
  docker compose -f "$DEPLOY_DIR/docker-compose.prod.yml" --env-file "$env_file" up -d --no-build --remove-orphans
}

switch_compose() {
  local env_file="${BLUEDOCK_ENV_FILE:-$DEPLOY_DIR/.env.prod}"
  if [[ ! -f "$env_file" ]]; then
    echo "Missing $env_file — copy deploy/.env.prod.example and configure." >&2
    exit 1
  fi

  echo "==> Pre-pull and switch (tag=${TAG})"
  compose_up "$TAG"

  if [[ "$VERIFY" == true ]]; then
    if ! wait_healthz; then
      if [[ -n "$PREV_TAG" && "$PREV_TAG" != "$TAG" ]]; then
        echo "==> Auto-rollback to ${PREV_TAG}" >&2
        compose_up "$PREV_TAG"
        wait_healthz || true
      fi
      exit 1
    fi
    if ! wait_readyz; then
      if [[ -n "$PREV_TAG" && "$PREV_TAG" != "$TAG" ]]; then
        echo "==> Readyz failed, auto-rollback to ${PREV_TAG}" >&2
        compose_up "$PREV_TAG"
      fi
      exit 1
    fi
  fi
}

switch_k8s() {
  local boot="${REGISTRY}/bluedock-boot:${TAG}"
  local wn="${REGISTRY}/bluedock-worker-notify:${TAG}"
  local wi="${REGISTRY}/bluedock-worker-index:${TAG}"

  echo "==> kubectl set image (namespace=${NAMESPACE}, tag=${TAG})"
  kubectl -n "$NAMESPACE" set image deployment/bluedock-boot bluedock-boot="$boot"
  kubectl -n "$NAMESPACE" set image deployment/bluedock-worker-notify worker="$wn"
  kubectl -n "$NAMESPACE" set image deployment/bluedock-worker-index worker="$wi"

  if ! kubectl -n "$NAMESPACE" rollout status deployment/bluedock-boot --timeout=300s; then
    echo "==> bluedock-boot rollout failed, undoing" >&2
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-boot || true
    exit 1
  fi
  if ! kubectl -n "$NAMESPACE" rollout status deployment/bluedock-worker-notify --timeout=300s; then
    echo "==> bluedock-worker-notify rollout failed, undoing all" >&2
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-boot || true
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-worker-notify || true
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-worker-index || true
    exit 1
  fi
  if ! kubectl -n "$NAMESPACE" rollout status deployment/bluedock-worker-index --timeout=300s; then
    echo "==> bluedock-worker-index rollout failed, undoing all" >&2
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-boot || true
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-worker-notify || true
    kubectl -n "$NAMESPACE" rollout undo deployment/bluedock-worker-index || true
    exit 1
  fi
}

case "$TARGET" in
  compose) switch_compose ;;
  k8s) switch_k8s ;;
  *)
    echo "Unknown --target: $TARGET (use compose or k8s)" >&2
    exit 1
    ;;
esac

write_state

env_file="${BLUEDOCK_ENV_FILE:-$DEPLOY_DIR/.env.prod}"
if [[ "$TARGET" == "compose" && -f "$env_file" ]]; then
  # 去掉废弃 BLUEDOCK_IMAGE_TAG / BLUEDOCK_REGISTRY（前缀改写在 compose 文件）
  if grep -qE '^(export )?(BLUEDOCK_IMAGE_TAG|BLUEDOCK_REGISTRY)=' "$env_file" 2>/dev/null; then
    tmp="$(mktemp)"
    grep -v -E '^(export )?(BLUEDOCK_IMAGE_TAG|BLUEDOCK_REGISTRY)=' "$env_file" >"$tmp" || true
    mv "$tmp" "$env_file"
  fi
  bash "$ROOT/deploy/scripts/env-file-upsert.sh" "$env_file" BLUEDOCK_VERSION "${TAG%-dirty}"
fi

echo "Switched to ${TAG}"
[[ -n "$PREV_TAG" && "$PREV_TAG" != "$TAG" ]] && echo "Rollback: bash deploy/scripts/prod-rollback.sh"
