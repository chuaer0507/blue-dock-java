#!/usr/bin/env bash
# 输出镜像 tag：优先 git describe --tags --always --dirty；无 tag 时 short sha；失败则 dev-unknown。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

git -C "$ROOT" describe --tags --always --dirty 2>/dev/null \
  || echo "dev-$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
