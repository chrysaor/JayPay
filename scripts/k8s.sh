#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
profile=jaypay
namespace=jaypay
rendered=build/charts/local
kubernetes_version=v1.32.0
ingress_port=${K8S_INGRESS_PORT:-8480}
mkdir -p build/k8s
export KUBECONFIG=$root/build/k8s/kubeconfig

log() { echo "k8s: $*"; }
fail() { echo "k8s: FAILED: $*" >&2; exit 1; }
cluster() { kubectl --context "$profile" "$@"; }
kube() { cluster --namespace "$namespace" "$@"; }
node_docker() { (eval "$(minikube --profile "$profile" docker-env --shell bash)" && docker "$@"); }
rolled_out() {
  local name
  for name in $(kube get "$1" --output name); do
    kube rollout status "$name" --timeout=900s >/dev/null
  done
}
changed_images=()

local_images() {
  python3 - "$rendered.json" <<'PY'
import json, sys
for line in open(sys.argv[1]):
    manifest = json.loads(line)
    if manifest["kind"] == "Deployment":
        container = manifest["spec"]["template"]["spec"]["containers"][0]
        if container.get("imagePullPolicy") == "Never":
            print(manifest["metadata"]["name"], container["image"])
PY
}

load_images() {
  local name image built sources=() targets=() names=()
  while read -r name image; do
    built=$(docker image inspect --format '{{.Created}}' "jaypay/$name" 2>/dev/null) || fail "jaypay/$name is missing, run make images"
    [ "$(node_docker image inspect --format '{{.Created}}' "$image" 2>/dev/null || true)" = "$built" ] && continue
    sources+=("jaypay/$name")
    targets+=("$image")
    names+=("$name")
  done < <(local_images)
  [ ${#sources[@]} -gt 0 ] || { log "the node already has every working tree image"; return; }
  log "loading ${#sources[@]} working tree image(s) into the node"
  docker save "${sources[@]}" | node_docker load --quiet >/dev/null
  for index in "${!sources[@]}"; do
    node_docker tag "${sources[$index]}" "${targets[$index]}"
  done
  changed_images=("${names[@]}")
}

up() {
  local started=$SECONDS workloads_started deployed name
  scripts/verify.sh charts
  if [ "$(minikube status --profile "$profile" --format '{{.Host}} {{.Kubelet}} {{.APIServer}}' 2>/dev/null)" = "Running Running Running" ]; then
    log "the minikube profile $profile is already running"
    minikube update-context --profile "$profile" >/dev/null
  else
    minikube start --profile "$profile" --driver docker --container-runtime docker \
      --kubernetes-version "$kubernetes_version" --cpus "${K8S_CPUS:-max}" --memory "${K8S_MEMORY:-10g}" --addons ingress
  fi
  cluster create namespace "$namespace" --dry-run=client --output yaml | cluster apply --server-side --filename - >/dev/null
  kube create secret generic jaypay --from-env-file=infra/docker/.env --dry-run=client --output yaml |
    kube apply --server-side --filename - >/dev/null
  log "applying the dependencies"
  kube apply --server-side --filename "$rendered" --selector app.kubernetes.io/component=dependency
  load_images
  cluster --namespace ingress-nginx wait --for=condition=Available deployment/ingress-nginx-controller --timeout=300s
  rolled_out statefulset
  log "dependencies ready after $((SECONDS - started))s"

  deployed=$(kube get deployment --output name)
  workloads_started=$SECONDS
  kube apply --server-side --filename "$rendered"
  for name in ${changed_images[@]+"${changed_images[@]}"}; do
    if grep -qx "deployment.apps/$name" <<<"$deployed"; then
      kube rollout restart deployment "$name"
    fi
  done
  rolled_out deployment
  log "$(kube get deployment --output name | wc -l | tr -d ' ') workloads ready $((SECONDS - workloads_started))s after they were applied, $((SECONDS - started))s in total"
  log "KUBECONFIG=$KUBECONFIG kubectl --namespace $namespace get pods"
}

smoke() {
  local web admin forward
  web=$(kube get ingress frontend-web --output jsonpath='{.spec.rules[0].host}')
  admin=$(kube get ingress frontend-admin --output jsonpath='{.spec.rules[0].host}')
  kubectl --context "$profile" --namespace ingress-nginx port-forward service/ingress-nginx-controller "$ingress_port:80" >build/k8s/port-forward.log 2>&1 &
  forward=$!
  trap "kill $forward 2>/dev/null || true" EXIT
  for _ in $(seq 1 30); do
    curl -s -o /dev/null "http://$web:$ingress_port/" && break
    kill -0 "$forward" 2>/dev/null || fail "port-forward to the ingress stopped: $(cat build/k8s/port-forward.log)"
    sleep 1
  done
  SMOKE_STACK=kubernetes SMOKE_WEB_URL="http://$web:$ingress_port" SMOKE_ADMIN_URL="http://$admin:$ingress_port" scripts/smoke.sh
}

down() {
  minikube delete --profile "$profile"
  rm -f "$KUBECONFIG"
}

case "${1:-}" in
  up) up ;;
  smoke) smoke ;;
  down) down ;;
  *) echo "usage: $0 up|smoke|down" >&2; exit 2 ;;
esac
