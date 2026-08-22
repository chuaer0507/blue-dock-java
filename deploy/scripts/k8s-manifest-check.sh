#!/usr/bin/env bash
# 无集群预检：kubectl kustomize 渲染 staging/prod，断言关键资源存在。
# 用法:
#   bash deploy/scripts/k8s-manifest-check.sh
#   bash deploy/scripts/k8s-manifest-check.sh --apply-dry-run
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
K8S_ROOT="${ROOT}/deploy/k8s"
APPLY_DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply-dry-run)
      APPLY_DRY_RUN=true
      shift
      ;;
    -h | --help)
      sed -n '2,6p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "$K8S_ROOT" ]] || [[ -z "$(find "$K8S_ROOT" -name 'kustomization.y*ml' 2>/dev/null | head -1)" ]]; then
  echo "k8s-manifest-check: 尚无 Kustomize 清单，跳过"
  exit 0
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "kubectl not found. Install kubectl (bundled kustomize) then retry." >&2
  exit 1
fi

fail=0

assert_named_kind() {
  local rendered="$1"
  local kind="$2"
  local name="$3"
  local overlay="$4"
  if printf '%s\n' "$rendered" | awk -v kind="$kind" -v name="$name" '
    $0 == "kind: " kind { k=1; next }
    k && $0 ~ /^[[:space:]]*name:[[:space:]]+/ {
      gsub(/^[[:space:]]*name:[[:space:]]+/, "", $0)
      gsub(/[[:space:]]+$/, "", $0)
      if ($0 == name) { found=1; exit }
    }
    /^---$/ { k=0 }
    END { exit found ? 0 : 1 }
  '; then
    echo "  OK   ${overlay}: ${kind}/${name}"
  else
    echo "  MISS ${overlay}: ${kind}/${name}"
    fail=$((fail + 1))
  fi
}

check_overlay() {
  local overlay="$1"
  local path="${K8S_ROOT}/overlays/${overlay}"
  local expect_ingress="${2:-false}"

  echo "==> kustomize overlays/${overlay}"
  if [[ ! -d "$path" ]]; then
    echo "  FAIL missing ${path}"
    fail=$((fail + 1))
    return
  fi

  local rendered
  if ! rendered="$(kubectl kustomize "$path" 2>&1)"; then
    echo "  FAIL kubectl kustomize ${path}"
    echo "$rendered" | sed 's/^/    /'
    fail=$((fail + 1))
    return
  fi

  local ns
  ns="$(printf '%s\n' "$rendered" | awk '
    $0 == "kind: Namespace" { n=1; next }
    n && $0 ~ /^[[:space:]]*name:[[:space:]]+/ {
      gsub(/^[[:space:]]*name:[[:space:]]+/, "", $0)
      gsub(/[[:space:]]+$/, "", $0)
      print; exit
    }
    /^---$/ { n=0 }
  ')"
  if [[ -z "$ns" ]]; then
    echo "  MISS ${overlay}: Namespace"
    fail=$((fail + 1))
  else
    echo "  OK   ${overlay}: Namespace/${ns}"
  fi

  assert_named_kind "$rendered" Deployment bluedock-boot "$overlay"
  assert_named_kind "$rendered" Deployment bluedock-worker-notify "$overlay"
  assert_named_kind "$rendered" Deployment bluedock-worker-index "$overlay"
  assert_named_kind "$rendered" Service bluedock-boot "$overlay"
  assert_named_kind "$rendered" PersistentVolumeClaim bluedock-uploads "$overlay"

  if [[ "$expect_ingress" == true ]]; then
    assert_named_kind "$rendered" Ingress bluedock-api "$overlay"
  fi

  if [[ "$APPLY_DRY_RUN" == true ]]; then
    echo "==> kubectl apply --dry-run=client -k overlays/${overlay}"
    if kubectl apply --dry-run=client -k "$path" >/dev/null; then
      echo "  OK   dry-run client overlays/${overlay}"
    else
      echo "  FAIL dry-run client overlays/${overlay} (need reachable kubeconfig?)"
      fail=$((fail + 1))
    fi
  fi
}

check_overlay staging false
check_overlay prod true

echo ""
if [[ "$fail" -eq 0 ]]; then
  echo "k8s-manifest-check: OK"
  if [[ "$APPLY_DRY_RUN" != true ]]; then
    echo "Optional: bash deploy/scripts/k8s-manifest-check.sh --apply-dry-run"
  fi
  exit 0
fi

echo "k8s-manifest-check: ${fail} failure(s)"
exit 1
