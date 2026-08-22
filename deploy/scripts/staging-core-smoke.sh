#!/usr/bin/env bash
# 核心 API smoke（经 Nginx / BASE_URL）。Compose / boot 未就绪时失败。
# 若 deploy/.env.dev 含 #admin账号 / #admin密码，额外校验超管登录。
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
ENV_FILE="${ENV_FILE:-${root}/deploy/.env.dev}"

bd_curl() {
  curl --noproxy '*' -sf --max-time 30 "$@"
}

encrypt_password() {
  local pem="$1"
  local plain="$2"
  local pem_file
  pem_file="$(mktemp)"
  if [[ "$pem" == *BEGIN* ]]; then
    printf '%s\n' "$pem" >"$pem_file"
  else
    python3 -c "
import textwrap, sys
b = sys.argv[1].strip()
print('-----BEGIN PUBLIC KEY-----')
print('\\n'.join(textwrap.wrap(b, 64)))
print('-----END PUBLIC KEY-----')
" "$pem" >"$pem_file"
  fi
  echo -n "$plain" | openssl pkeyutl -encrypt -pubin -inkey "$pem_file" \
    -pkeyopt rsa_padding_mode:oaep \
    -pkeyopt rsa_oaep_md:sha256 \
    -pkeyopt rsa_mgf1_md:sha256 2>/dev/null | base64 | tr -d '\n'
  rm -f "$pem_file"
}

ADMIN_EMAIL=""
ADMIN_PASSWORD=""
if [[ -f "$ENV_FILE" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      \#admin账号：*) ADMIN_EMAIL="${ADMIN_EMAIL:-${line#\#admin账号：}}" ;;
      \#admin密码：*) ADMIN_PASSWORD="${ADMIN_PASSWORD:-${line#\#admin密码：}}" ;;
    esac
  done < "$ENV_FILE"
fi

echo "staging-core-smoke: BASE_URL=${BASE_URL}"

bd_curl "${BASE_URL}/healthz" >/dev/null
echo "  ok healthz"

key_json="$(bd_curl "${BASE_URL}/api/users/key/client")"
echo "$key_json" | grep -q 'keyId'
echo "  ok key/client"

ver_json="$(bd_curl "${BASE_URL}/api/system/version")"
echo "$ver_json" | grep -q 'version'
echo "  ok system/version"

bd_curl "${BASE_URL}/api/privacy" >/dev/null
echo "  ok privacy"

if [[ -n "${ADMIN_EMAIL}" && -n "${ADMIN_PASSWORD}" ]]; then
  kid="$(printf '%s' "$key_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['keyId'])")"
  pub_key="$(printf '%s' "$key_json" | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['publicKey'])")"
  enc_pass="$(encrypt_password "$pub_key" "$ADMIN_PASSWORD")"
  login_json="$(
    bd_curl -G "${BASE_URL}/api/users/login" \
      --data-urlencode "email=${ADMIN_EMAIL}" \
      --data-urlencode "password=${enc_pass}" \
      --data-urlencode "keyId=${kid}"
  )"
  echo "$login_json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d.get('code') == 0, d
assert d.get('data', {}).get('token'), d
"
  echo "  ok users/login (admin ${ADMIN_EMAIL})"
else
  echo "  skip users/login (no #admin账号/#admin密码 in ${ENV_FILE})"
fi

echo "staging-core-smoke: passed"
