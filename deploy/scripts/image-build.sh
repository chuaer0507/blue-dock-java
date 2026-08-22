#!/usr/bin/env bash
# 本地或 CI 构建 bluedock-boot / Worker 镜像（不在生产机编译）。
# 用法:
#   bash deploy/scripts/image-build.sh [TAG]
#   TAG 默认: git describe（见 resolve-image-tag.sh）
# 环境变量:
#   BLUEDOCK_REGISTRY  可选；设置时额外打 ${BLUEDOCK_REGISTRY}/bluedock-* 标签（供推送 / K8s）
# 始终打 Compose 同名标签：bluedock-boot / bluedock-worker-*（不写回 .env 的仓库前缀）
# 构建后会把 BLUEDOCK_VERSION 写入 deploy/.env.dev 与 .env.prod（见 sync-env-image-tag.sh）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REGISTRY="${BLUEDOCK_REGISTRY:-}"

if [[ $# -ge 1 ]]; then
  TAG="$1"
else
  TAG="$(bash "$(dirname "$0")/resolve-image-tag.sh")"
fi

BOOT_LOCAL="bluedock-boot:${TAG}"
WN_LOCAL="bluedock-worker-notify:${TAG}"
WI_LOCAL="bluedock-worker-index:${TAG}"

echo "==> Building images with tag: ${TAG}"
docker build -f "$ROOT/deploy/docker/Dockerfile.boot" -t "$BOOT_LOCAL" "$ROOT"
docker build -f "$ROOT/deploy/docker/Dockerfile.worker-notify" -t "$WN_LOCAL" "$ROOT"
docker build -f "$ROOT/deploy/docker/Dockerfile.worker-index" -t "$WI_LOCAL" "$ROOT"

if [[ -n "$REGISTRY" ]]; then
  docker tag "$BOOT_LOCAL" "${REGISTRY}/bluedock-boot:${TAG}"
  docker tag "$WN_LOCAL" "${REGISTRY}/bluedock-worker-notify:${TAG}"
  docker tag "$WI_LOCAL" "${REGISTRY}/bluedock-worker-index:${TAG}"
fi

echo "==> Sync BLUEDOCK_VERSION into deploy/.env.*"
bash "$ROOT/deploy/scripts/sync-env-image-tag.sh" "$TAG"

echo ""
echo "Built (Compose):"
echo "  $BOOT_LOCAL"
echo "  $WN_LOCAL"
echo "  $WI_LOCAL"
if [[ -n "$REGISTRY" ]]; then
  echo "Also tagged for push:"
  echo "  ${REGISTRY}/bluedock-boot:${TAG}"
  echo "  ${REGISTRY}/bluedock-worker-notify:${TAG}"
  echo "  ${REGISTRY}/bluedock-worker-index:${TAG}"
  echo ""
  echo "Push:  BLUEDOCK_REGISTRY=${REGISTRY} bash deploy/scripts/image-push.sh ${TAG}"
else
  echo ""
  echo "Push:  BLUEDOCK_REGISTRY=<registry> bash deploy/scripts/image-push.sh ${TAG}"
fi
