#!/usr/bin/env bash
# 将 BLUEDOCK_VERSION（git tag，如 v1.0.0）写入 deploy/.env.dev 与 deploy/.env.prod。
# 镜像 tag 与应用版本统一为 BLUEDOCK_VERSION；镜像仓库前缀写死在 compose，不入 env。
# 默认：git describe --tags --always --dirty（见 resolve-image-tag.sh）。
# 用法:
#   bash deploy/scripts/sync-env-image-tag.sh [TAG]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DEPLOY_DIR="$ROOT/deploy"
UPSERT="$ROOT/deploy/scripts/env-file-upsert.sh"

if [[ $# -ge 1 ]]; then
  TAG="$1"
else
  TAG="$(bash "$ROOT/deploy/scripts/resolve-image-tag.sh")"
fi

VERSION="${TAG%-dirty}"

ensure_from_example() {
  local file="$1"
  local example="$2"
  if [[ ! -f "$file" && -f "$example" ]]; then
    cp "$example" "$file"
    echo "Created $file from $(basename "$example")"
  fi
}

# 删除已废弃的镜像相关 env 行
strip_legacy_keys() {
  local file="$1"
  [[ -f "$file" ]] || return 0
  local tmp
  tmp="$(mktemp)"
  grep -v -E '^(export )?(BLUEDOCK_IMAGE_TAG|BLUEDOCK_REGISTRY)=' "$file" >"$tmp" || true
  mv "$tmp" "$file"
}

ensure_from_example "$DEPLOY_DIR/.env.dev" "$DEPLOY_DIR/.env.dev.example"
ensure_from_example "$DEPLOY_DIR/.env.prod" "$DEPLOY_DIR/.env.prod.example"

write_one() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "skip (missing): $file"
    return 0
  fi
  strip_legacy_keys "$file"
  bash "$UPSERT" "$file" BLUEDOCK_VERSION "$VERSION"
  echo "Updated $(basename "$file"): BLUEDOCK_VERSION=${VERSION}"
}

write_one "$DEPLOY_DIR/.env.dev"
write_one "$DEPLOY_DIR/.env.prod"
