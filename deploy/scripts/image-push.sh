#!/usr/bin/env bash
# 推送已构建镜像到 Registry。
# 用法:
#   BLUEDOCK_REGISTRY=ghcr.io/myorg bash deploy/scripts/image-push.sh 1.0.0-a1b2c3d
# 若本地仅有 Compose 裸名（bluedock-boot:TAG），会先 docker tag 再推送。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ ! -f "$ROOT/deploy/docker/Dockerfile.boot" ]]; then
  echo "image-push: Dockerfiles 尚未落地，跳过"
  exit 0
fi

REGISTRY="${BLUEDOCK_REGISTRY:-}"
if [[ -z "$REGISTRY" ]]; then
  echo "Usage: BLUEDOCK_REGISTRY=<registry> $0 <TAG>" >&2
  exit 1
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: BLUEDOCK_REGISTRY=<registry> $0 <TAG>" >&2
  exit 1
fi
TAG="$1"

ensure_and_push() {
  local name="$1"
  local remote="${REGISTRY}/${name}:${TAG}"
  local local_img="${name}:${TAG}"
  if ! docker image inspect "$remote" >/dev/null 2>&1; then
    if docker image inspect "$local_img" >/dev/null 2>&1; then
      docker tag "$local_img" "$remote"
    else
      echo "ERROR: missing image $remote (and no local $local_img)" >&2
      exit 1
    fi
  fi
  echo "==> Pushing $remote"
  docker push "$remote"
}

ensure_and_push bluedock-boot
ensure_and_push bluedock-worker-notify
ensure_and_push bluedock-worker-index

echo "Pushed tag: ${TAG}"
