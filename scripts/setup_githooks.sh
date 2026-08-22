#!/usr/bin/env bash
# 将本仓库 git hooks 指向 .githooks/（不改全局 config）。
# 克隆后执行一次即可：bash scripts/setup_githooks.sh

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if [[ ! -d .git ]]; then
  echo "setup_githooks: 当前目录不是 git 仓库根" >&2
  exit 1
fi

if [[ ! -x .githooks/commit-msg ]]; then
  chmod +x .githooks/commit-msg
fi

git config core.hooksPath .githooks
echo "setup_githooks: core.hooksPath=.githooks （已启用 commit-msg 校验）"
