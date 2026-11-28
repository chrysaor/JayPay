#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
profile=jaypay
env_dir=deploy/envs/local
setting() { sed -n "s/^$1: *//p" "$env_dir/gitops.yaml"; }
namespace=$(setting namespace)
revision=$(setting revision)
repo_path=$(setting repoURL)
repo_path=${repo_path#file://}
rendered=build/charts/local
kubernetes_version=v1.32.0
timeout=${K8S_TIMEOUT:-1200}
host_git=$root/build/k8s/git
mirror=$host_git/$(basename "$repo_path")
mkdir -p build/k8s
export KUBECONFIG=$root/build/k8s/kubeconfig
export GIT_AUTHOR_NAME="make k8s-up" GIT_AUTHOR_EMAIL=k8s-up@jaypay.localhost
export GIT_COMMITTER_NAME=$GIT_AUTHOR_NAME GIT_COMMITTER_EMAIL=$GIT_AUTHOR_EMAIL

log() { echo "k8s: $*"; }
fail() { echo "k8s: FAILED: $*" >&2; exit 1; }
cluster() { kubectl --context "$profile" "$@"; }
kube() { cluster --namespace "$namespace" "$@"; }
argocd() { cluster --namespace argocd "$@"; }
mirror_git() { git --git-dir "$mirror" "$@"; }
node_docker() { (eval "$(minikube --profile "$profile" docker-env --shell bash)" && docker "$@"); }
changed_images=()
published=""

until_true() {
  local what=$1 deadline=$((SECONDS + timeout)) next_report=$((SECONDS + 30))
  shift
  : >build/k8s/last
  until "$@"; do
    [ "$SECONDS" -lt "$deadline" ] || fail "$what did not happen within ${timeout}s: $(cat build/k8s/last)"
    if [ "$SECONDS" -ge "$next_report" ]; then
      log "waiting until $what: $(cat build/k8s/last)"
      next_report=$((SECONDS + 30))
    fi
    sleep 2
  done
}

with_retries() {
  local attempt
  for attempt in 1 2 3 4 5; do
    "$@" && return
    log "attempt $attempt of: $* failed, retrying"
    sleep 10
  done
  fail "$* failed five times"
}

rendered_containers() {
  python3 - "$rendered.json" "$1" <<'PY'
import json, sys
for line in open(sys.argv[1]):
    manifest = json.loads(line)
    if manifest["kind"] == sys.argv[2]:
        container = manifest["spec"]["template"]["spec"]["containers"][0]
        print(manifest["metadata"]["name"], container["image"], container.get("imagePullPolicy", ""))
PY
}

stop_tree() {
  local child
  for child in $(pgrep -P "$1"); do
    stop_tree "$child"
  done
  kill "$1" 2>/dev/null || true
}

stop_pulls() {
  local pid
  for pid in $(jobs -p); do
    stop_tree "$pid"
  done
}

pull_dependency_images() {
  local name image policy
  while read -r name image policy; do
    (
      started=$SECONDS
      if node_docker pull --quiet "$image" >/dev/null 2>&1; then
        log "the node has the $name image after $((SECONDS - started))s"
      else
        log "the node could not pull $image, the kubelet will retry"
      fi
    ) &
  done < <(rendered_containers StatefulSet)
}

load_images() {
  local name image policy built sources=() targets=() names=()
  while read -r name image policy; do
    [ "$policy" = Never ] || continue
    built=$(docker image inspect --format '{{.Created}}' "jaypay/$name" 2>/dev/null) || fail "jaypay/$name is missing, run make images"
    [ "$(node_docker image inspect --format '{{.Created}}' "$image" 2>/dev/null || true)" = "$built" ] && continue
    sources+=("jaypay/$name")
    targets+=("$image")
    names+=("$name")
  done < <(rendered_containers Deployment)
  [ ${#sources[@]} -gt 0 ] || { log "the node already has every working tree image"; return; }
  log "loading ${#sources[@]} working tree image(s) into the node"
  docker save "${sources[@]}" | node_docker load --quiet >/dev/null
  for index in "${!sources[@]}"; do
    node_docker tag "${sources[$index]}" "${targets[$index]}"
  done
  changed_images=("${names[@]}")
}

start_cluster() {
  local mounts
  mkdir -p "$host_git"
  if [ "$(minikube status --profile "$profile" --format '{{.Host}} {{.Kubelet}} {{.APIServer}}' 2>/dev/null)" = "Running Running Running" ]; then
    mounts=$(docker inspect --format '{{range .Mounts}}{{.Source}}:{{.Destination}} {{end}}' "$profile")
    grep -qF "$host_git:$(dirname "$repo_path") " <<<"$mounts " ||
      fail "the running profile $profile does not mount $host_git, run make k8s-down first"
    cluster --namespace kube-system get daemonset kindnet >/dev/null 2>&1 ||
      fail "the running profile $profile has no kindnet, which enforces NetworkPolicy, run make k8s-down first"
    log "the minikube profile $profile is already running"
    minikube update-context --profile "$profile" >/dev/null
  else
    minikube start --profile "$profile" --driver docker --container-runtime docker \
      --kubernetes-version "$kubernetes_version" --cpus "${K8S_CPUS:-max}" --memory "${K8S_MEMORY:-14g}" --cni kindnet --addons ingress \
      --mount --mount-string "$host_git:$(dirname "$repo_path")"
  fi
}

publish_working_tree() {
  local objects index tree parent commit
  [ -d "$mirror" ] || git init --quiet --bare --initial-branch "$revision" "$mirror"
  objects=$(git rev-parse --path-format=absolute --git-path objects)
  index=$(mktemp "$root/build/k8s/index.XXXXXX")
  parent=$(mirror_git rev-parse --verify --quiet "refs/heads/$revision^{commit}" || true)
  tree=$(
    export GIT_INDEX_FILE=$index GIT_OBJECT_DIRECTORY=$mirror/objects GIT_ALTERNATE_OBJECT_DIRECTORIES=$objects
    git read-tree HEAD && git add --all && git write-tree
  )
  rm -f "$index"
  if [ -n "$parent" ] && [ "$(mirror_git rev-parse "$parent^{tree}")" = "$tree" ]; then
    published=$parent
    log "$revision in $mirror is already the working tree, ${published:0:12}"
    return
  fi
  published=$(GIT_ALTERNATE_OBJECT_DIRECTORIES=$objects mirror_git commit-tree "$tree" ${parent:+-p "$parent"} \
    -m "Working tree on top of $(git rev-parse --short HEAD)")
  mirror_git update-ref "refs/heads/$revision" "$published" ${parent:+"$parent"}
  GIT_ALTERNATE_OBJECT_DIRECTORIES=$objects mirror_git repack -a -d -q
  log "published the working tree to $revision in $mirror as ${published:0:12}"
}

applications() {
  argocd get applications --selector "app.kubernetes.io/part-of=jaypay${1:+,app.kubernetes.io/component=$1}" \
    --output jsonpath='{range .items[*]}{.metadata.name} {.status.sync.revision} {.status.sync.status} {.status.health.status}{"\n"}{end}'
}

applications_converged() {
  local component=$1 expected=$2 total ready
  total=$(applications "$component" | grep -c . || true)
  ready=$(applications "$component" | grep -c " $published Synced Healthy$" || true)
  echo "$ready of $expected ${component:-applications} synced to ${published:0:12} and healthy, $total generated" >build/k8s/last
  [ "$total" -eq "$expected" ] && [ "$ready" -eq "$expected" ]
}

install_argocd() {
  cluster apply --server-side --force-conflicts --filename build/charts/argocd/local.yaml >/dev/null
}

install_envoy_gateway() {
  cluster apply --server-side --force-conflicts --filename build/charts/envoy-gateway/local.yaml >/dev/null
}

gateway_service() {
  cluster --namespace envoy-gateway-system get service --output name \
    --selector "gateway.envoyproxy.io/owning-gateway-namespace=$namespace,gateway.envoyproxy.io/owning-gateway-name=gateway"
}

up() {
  local started=$SECONDS deployed name workload_files dependency_files
  scripts/verify.sh charts
  start_cluster
  cluster create namespace "$namespace" --dry-run=client --output yaml | cluster apply --server-side --filename - >/dev/null
  kube create secret generic jaypay --from-env-file=infra/docker/.env --dry-run=client --output yaml |
    kube apply --server-side --filename - >/dev/null
  log "installing Argo CD"
  with_retries install_argocd
  log "installing Envoy Gateway"
  with_retries install_envoy_gateway
  trap stop_pulls EXIT
  pull_dependency_images
  publish_working_tree
  deployed=$(kube get deployment --output name)
  load_images
  argocd wait --for=condition=Established crd/applicationsets.argoproj.io crd/applications.argoproj.io --timeout=60s >/dev/null
  argocd rollout status statefulset/argocd-application-controller --timeout=600s >/dev/null
  for name in argocd-applicationset-controller argocd-repo-server argocd-redis; do
    argocd rollout status "deployment/$name" --timeout=600s >/dev/null
  done
  log "Argo CD ready after $((SECONDS - started))s"
  cluster wait --for=condition=Established crd/gateways.gateway.networking.k8s.io crd/envoyproxies.gateway.envoyproxy.io --timeout=60s >/dev/null
  for name in envoy-gateway valkey; do
    cluster --namespace envoy-gateway-system rollout status "deployment/$name" --timeout=600s >/dev/null
  done
  log "Envoy Gateway ready after $((SECONDS - started))s"
  cluster apply --server-side --filename "$rendered/gitops.yaml" >/dev/null
  for name in ${changed_images[@]+"${changed_images[@]}"}; do
    if grep -qx "deployment.apps/$name" <<<"$deployed"; then
      kube rollout restart deployment "$name"
    fi
  done
  cluster --namespace ingress-nginx wait --for=condition=Available deployment/ingress-nginx-controller --timeout=300s >/dev/null
  dependency_files=$(ls "$env_dir"/dependency/*.yaml | wc -l | tr -d ' ')
  workload_files=$(ls "$env_dir"/*/*.yaml | wc -l | tr -d ' ')
  until_true "the dependencies are synced and healthy" applications_converged dependency "$dependency_files"
  log "dependencies ready after $((SECONDS - started))s"
  until_true "every application is synced and healthy" applications_converged "" "$workload_files"
  wait
  log "$workload_files applications synced to ${published:0:12} and healthy after $((SECONDS - started))s"
  log "KUBECONFIG=$KUBECONFIG kubectl --namespace $namespace get pods"
}

forwards=()
scaled_file=""
scaled_original=""
forwarded_port=""

stop_forwards() {
  [ ${#forwards[@]} -eq 0 ] || kill "${forwards[@]}" 2>/dev/null || true
}

forward() {
  local name=$1 target_namespace=$2 target=$3 ports=$4 output=build/k8s/port-forward-$1.log
  kubectl --context "$profile" --namespace "$target_namespace" port-forward "$target" "$ports" >"$output" 2>&1 &
  forwards+=($!)
  for _ in $(seq 1 30); do
    forwarded_port=$(sed -n 's/^Forwarding from 127\.0\.0\.1:\([0-9]*\) .*/\1/p' "$output" | head -1)
    [ -z "$forwarded_port" ] || return 0
    kill -0 "${forwards[${#forwards[@]} - 1]}" 2>/dev/null || fail "port-forward to $name stopped: $(cat "$output")"
    sleep 1
  done
  fail "port-forward to $name did not start: $(cat "$output")"
}

smoke() {
  local web_port admin_port clickhouse prometheus banking money grafana vault
  web_port=$(kube get gateway gateway --output jsonpath='{.spec.listeners[?(@.name=="web")].port}')
  admin_port=$(kube get gateway gateway --output jsonpath='{.spec.listeners[?(@.name=="admin")].port}')
  trap stop_forwards EXIT
  forward clickhouse "$namespace" service/clickhouse :8123
  clickhouse=$forwarded_port
  forward prometheus "$namespace" service/prometheus :9090
  prometheus=$forwarded_port
  forward banking-service "$namespace" service/banking-service :8080
  banking=$forwarded_port
  forward keycloak "$namespace" service/keycloak 8092:8080
  forward money-service "$namespace" service/money-service :8080
  money=$forwarded_port
  forward grafana "$namespace" service/grafana :3000
  grafana=$forwarded_port
  forward vault "$namespace" service/vault :8200
  vault=$forwarded_port
  forward gateway envoy-gateway-system "$(gateway_service)" "$admin_port:$admin_port"
  forward gateway-web envoy-gateway-system "$(gateway_service)" "$web_port:$web_port"
  until_true "the gateway answers through the port-forward" curl -s -o /dev/null "http://localhost:$admin_port/"
  until_true "the gateway answers for the customer site" curl -s -o /dev/null "http://localhost:$web_port/"
  SMOKE_STACK=kubernetes SMOKE_NAMESPACE=$namespace SMOKE_WEB_URL="http://localhost:$web_port" SMOKE_ADMIN_URL="http://localhost:$admin_port" \
    SMOKE_CLICKHOUSE_URL="http://127.0.0.1:$clickhouse" SMOKE_PROMETHEUS_URL="http://127.0.0.1:$prometheus" \
    SMOKE_BANKING_URL="http://127.0.0.1:$banking" SMOKE_KEYCLOAK_URL=http://localhost:8092 SMOKE_MONEY_URL="http://127.0.0.1:$money" \
    SMOKE_GRAFANA_URL="http://127.0.0.1:$grafana" SMOKE_VAULT_URL="http://127.0.0.1:$vault" \
    SMOKE_SERVICE_URLS="http://127.0.0.1:$banking http://127.0.0.1:$money" scripts/smoke.sh
}

commit_to_mirror() {
  local message=$1 path=$2 content=$3 parent index blob tree commit
  parent=$(mirror_git rev-parse "refs/heads/$revision")
  index=$(mktemp "$root/build/k8s/index.XXXXXX")
  blob=$(mirror_git hash-object -w --stdin <<<"$content")
  tree=$(
    export GIT_INDEX_FILE=$index
    mirror_git read-tree "$parent" && mirror_git update-index --cacheinfo "100644,$blob,$path" && mirror_git write-tree
  )
  rm -f "$index"
  commit=$(mirror_git commit-tree "$tree" -p "$parent" -m "$message")
  mirror_git update-ref "refs/heads/$revision" "$commit" "$parent"
  echo "$commit"
}

replicas_are() {
  local deployment=$1 want=$2 actual
  actual=$(kube get deployment "$deployment" --output jsonpath='{.spec.replicas} {.status.availableReplicas}')
  echo "$deployment has replicas ${actual% *}, ${actual#* } available" >build/k8s/last
  [ "$actual" = "$want $want" ]
}

application_at() {
  local application=$1 commit=$2 status
  status=$(argocd get application "$application" --output jsonpath='{.status.sync.revision} {.status.sync.status}')
  echo "$application is at ${status:0:12}, ${status#* }" >build/k8s/last
  [ "$status" = "$commit Synced" ]
}

restore_scaled_file() {
  [ -z "$scaled_file" ] || commit_to_mirror "Restore $scaled_file" "$scaled_file" "$scaled_original" >/dev/null
}

gitops_check() {
  local workload=frontend-web file original replicas scaled commit started
  file=$env_dir/frontend/$workload.yaml
  [ -d "$mirror" ] || fail "$mirror is missing, run make k8s-up first"
  original=$(mirror_git show "refs/heads/$revision:$file")
  replicas=$(sed -n 's/^replicas: *//p' <<<"$original")
  replicas=${replicas:-1}
  scaled=$((replicas + 1))
  until_true "$workload runs $replicas replicas" replicas_are "$workload" "$replicas"

  scaled_file=$file scaled_original=$original
  trap restore_scaled_file EXIT
  commit=$(commit_to_mirror "Scale $workload to $scaled replicas" "$file" "$(grep -v '^replicas:' <<<"$original")
replicas: $scaled")
  started=$SECONDS
  log "committed ${commit:0:12} to $revision, which sets $file to $scaled replicas"
  until_true "the commit reaches the cluster" replicas_are "$workload" "$scaled"
  until_true "local-$workload is synced to the commit" application_at "local-$workload" "$commit"
  log "PASSED: the commit reached the cluster in $((SECONDS - started))s, nothing was applied by hand"

  commit=$(commit_to_mirror "Scale $workload back to $replicas replicas" "$file" "$original")
  scaled_file=""
  until_true "the revert commit reaches the cluster" replicas_are "$workload" "$replicas"
  until_true "local-$workload is synced to the revert commit" application_at "local-$workload" "$commit"
  log "committed ${commit:0:12}, which restores $file, and the cluster followed"

  kube scale deployment "$workload" --replicas "$((replicas + 2))" >/dev/null
  started=$SECONDS
  log "scaled $workload to $((replicas + 2)) replicas by hand"
  until_true "Argo CD reverts the manual scale" replicas_are "$workload" "$replicas"
  log "PASSED: Argo CD reverted the manual scale in $((SECONDS - started))s"
}

policy_check() {
  [ -f "$rendered.json" ] || fail "$rendered.json is missing, run make k8s-up first"
  python3 scripts/policy-check.py "$root" "$rendered.json" "$namespace" "$profile"
}

down() {
  minikube delete --profile "$profile"
  rm -rf "$KUBECONFIG" "$host_git"
}

case "${1:-}" in
  up) up ;;
  smoke) smoke ;;
  gitops-check) gitops_check ;;
  policy-check) policy_check ;;
  down) down ;;
  *) echo "usage: $0 up|smoke|gitops-check|policy-check|down" >&2; exit 2 ;;
esac
