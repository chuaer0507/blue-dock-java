#!/usr/bin/env bash
# 更新 deploy/.env.* 中的 KEY=value（存在则替换，不存在则追加）。
# 用法: bash deploy/scripts/env-file-upsert.sh <file> KEY value
set -euo pipefail

[[ $# -eq 3 ]] || {
  echo "Usage: $0 <env-file> <KEY> <value>" >&2
  exit 1
}

FILE="$1"
KEY="$2"
VALUE="$3"

[[ -f "$FILE" ]] || exit 0

tmp="$(mktemp)"
found=0
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" == "${KEY}="* ]] || [[ "$line" == "export ${KEY}="* ]]; then
    printf '%s=%s\n' "$KEY" "$VALUE"
    found=1
  else
    printf '%s\n' "$line"
  fi
done <"$FILE" >"$tmp"

if [[ "$found" -eq 0 ]]; then
  printf '%s=%s\n' "$KEY" "$VALUE" >>"$tmp"
fi

mv "$tmp" "$FILE"
