#!/usr/bin/env bash
set -uo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root" || exit 1

TERRAFORM_IMAGE=hashicorp/terraform:1.17.2@sha256:6e9c1e647c31a960dc9b59e13272f9111d90010c10b717f77ffb324767660475
ACTIONLINT_IMAGE=rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667
HELM_IMAGE=alpine/helm:4.3.2@sha256:a3b9d617c0c30a6c488cebb429c4ba83995a26dc2184599af5fd408a361b2e67
KUBECONFORM_IMAGE=ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e
KUBERNETES_VERSION=1.34.0
ALL_STEPS=(contract english env terraform actionlint charts frontend backend)

step_contract() {
  python3 scripts/extract-endpoints.py backend | diff -u docs/api-contract.md -
}

step_english() {
  python3 scripts/check-english.py .
}

step_env() {
  python3 scripts/check-env.py .
}

terraform_run() {
  local dir=$1
  shift
  docker run --rm --user "$(id -u):$(id -g)" -e CHECKPOINT_DISABLE=1 \
    -v "$root/infra/terraform:/tf" -w "/tf/$dir" "$TERRAFORM_IMAGE" "$@"
}

step_terraform() {
  terraform_run . fmt -check -recursive -diff &&
    terraform_run envs/dev init -backend=false -input=false -no-color >/dev/null &&
    terraform_run envs/dev validate -no-color &&
    terraform_run envs/dev test -no-color
}

step_actionlint() {
  local workflows=(.github/workflows/*.yml)
  docker run --rm -v "$root:/repo" -w /repo "$ACTIONLINT_IMAGE" -color "${workflows[@]}"
}

step_charts() {
  local out=build/charts
  rm -rf "$out" && mkdir -p "$out" &&
    docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$root:/repo" -w /repo \
      --entrypoint sh "$HELM_IMAGE" scripts/render-charts.sh "$out" &&
    docker run --rm -v "$root/$out:/rendered:ro" "$KUBECONFORM_IMAGE" \
      -strict -summary -kubernetes-version "$KUBERNETES_VERSION" -ignore-filename-pattern '\.json$' /rendered &&
    python3 scripts/check-deploy.py . "$out"
}

step_frontend() {
  (cd frontend && npm ci --no-audit --no-fund && npm run lint && npm test && npm run build)
}

step_backend() {
  (cd backend && ./gradlew build)
}

steps=("$@")
[ ${#steps[@]} -gt 0 ] || steps=("${ALL_STEPS[@]}")
for step in "${steps[@]}"; do
  declare -F "step_$step" >/dev/null || { echo "unknown step: $step (known: ${ALL_STEPS[*]})" >&2; exit 2; }
done

failed=()
for step in "${steps[@]}"; do
  echo "==> $step"
  if "step_$step"; then
    echo "<== $step ok"
  else
    echo "<== $step FAILED"
    failed+=("$step")
  fi
done

if [ ${#failed[@]} -gt 0 ]; then
  echo "FAILED: ${failed[*]}"
  exit 1
fi
echo "PASSED: ${steps[*]}"
