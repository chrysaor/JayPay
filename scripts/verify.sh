#!/usr/bin/env bash
set -uo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root" || exit 1

TERRAFORM_IMAGE=hashicorp/terraform:1.17.2@sha256:6e9c1e647c31a960dc9b59e13272f9111d90010c10b717f77ffb324767660475
ACTIONLINT_IMAGE=rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667
HELM_IMAGE=alpine/helm:4.3.2@sha256:a3b9d617c0c30a6c488cebb429c4ba83995a26dc2184599af5fd408a361b2e67
KUBECONFORM_IMAGE=ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e
KUBECTL_IMAGE=registry.k8s.io/kubectl:v1.34.9@sha256:06a7ce6adc8e89486cedf0afe1e818c10e1f4da2d45493bf2ee87336838e3187
KUBERNETES_VERSION=1.34.0
ARGOCD_VERSION=v3.5.9
ARGOCD_INSTALL_SHA256=6eb366a0a0719a82ea084e382ce09ac67be53da3c3190c2ede8f82e4102e87d6
ALL_STEPS=(changelog contract docs english env terraform actionlint charts frontend backend)

step_changelog() {
  python3 scripts/changelog.py check CHANGELOG.md &&
    { [ -z "${CHANGELOG_BASE:-}" ] || python3 scripts/changelog.py entry CHANGELOG.md "$CHANGELOG_BASE"; }
}

step_contract() {
  python3 scripts/extract-endpoints.py backend | diff -u docs/api-contract.md -
}

step_docs() {
  python3 scripts/check-docs.py .
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

argocd_install() {
  local out=$1 cache=${ARGOCD_CACHE:-$root/build/argocd} overlay stage status
  local install=$cache/core-install-$ARGOCD_VERSION.yaml
  mkdir -p "$cache" "$out" build/argocd || return
  if [ ! -f "$install" ]; then
    curl -fsSL -o "$install.part" "https://raw.githubusercontent.com/argoproj/argo-cd/$ARGOCD_VERSION/manifests/core-install.yaml" &&
      mv "$install.part" "$install" || return
  fi
  echo "$ARGOCD_INSTALL_SHA256  $install" | shasum -a 256 -c --quiet - ||
    { echo "argocd: core-install.yaml of $ARGOCD_VERSION does not match ARGOCD_INSTALL_SHA256" >&2; return 1; }
  for overlay in deploy/argocd/*/; do
    stage=$(mktemp -d build/argocd/stage.XXXXXX) || return
    cp "$overlay"* "$stage/" && cp "$install" "$stage/core-install.yaml" &&
      docker run --rm -v "$root/$stage:/overlay:ro" "$KUBECTL_IMAGE" kustomize /overlay >"$out/$(basename "$overlay").yaml"
    status=$?
    rm -rf "$stage"
    [ "$status" -eq 0 ] || return "$status"
  done
}

step_render() {
  local out=build/charts
  rm -rf "$out" && mkdir -p "$out" &&
    argocd_install "$out/argocd" &&
    docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$root:/repo" -w /repo \
      --entrypoint sh "$HELM_IMAGE" scripts/render-charts.sh "$out"
}

step_charts() {
  local out=build/charts schemas=build/argocd/schemas
  rm -rf "$schemas" && step_render &&
    python3 scripts/crd-schemas.py "$out/argocd.json" "$schemas" &&
    docker run --rm -v "$root/$out:/rendered:ro" -v "$root/$schemas:/schemas:ro" "$KUBECONFORM_IMAGE" \
      -strict -summary -kubernetes-version "$KUBERNETES_VERSION" -ignore-filename-pattern '\.json$' -skip CustomResourceDefinition \
      -schema-location default -schema-location '/schemas/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' /rendered &&
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
