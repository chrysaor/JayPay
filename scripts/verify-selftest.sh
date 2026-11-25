#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$root/build"
work=$(mktemp -d "$root/build/verify-selftest.XXXXXX")
trap 'rm -rf "$work"' EXIT
failures=0
export ARGOCD_CACHE=$root/build/argocd

copy_repo() {
  local dest="$work/$1"
  mkdir -p "$dest"
  git -C "$root" ls-files -co --exclude-standard -- scripts README.md docs backend deploy infra/terraform infra/docker frontend/Dockerfile frontend/apps .github |
    while IFS= read -r path; do
      [ -e "$root/$path" ] || continue
      mkdir -p "$dest/$(dirname "$path")"
      cp -P "$root/$path" "$dest/$path"
    done
  echo "$dest"
}

expect() {
  local step=$1 want=$2 dir=$3 planted=${4:-}
  local out status=0
  out=$("$dir/scripts/verify.sh" "$step" 2>&1) || status=$?
  if [ "$want" = pass ] && [ "$status" -eq 0 ]; then
    echo "ok   $step passes on a clean copy"
  elif [ "$want" = fail ] && [ "$status" -ne 0 ] && grep -q "^FAILED: $step$" <<<"$out" && grep -q "$planted" <<<"$out"; then
    echo "ok   $step fails on the planted $planted"
  else
    echo "FAIL $step (exit $status, wanted $want)"
    echo "$out" | tail -20
    failures=$((failures + 1))
  fi
}

clean=$(copy_repo clean)
expect contract pass "$clean"
expect docs pass "$clean"
expect actionlint pass "$clean"
expect english pass "$clean"
expect env pass "$clean"
expect charts pass "$clean"

dir=$(copy_repo contract)
echo '| banking-service | GET | /banking/not-in-code |' >>"$dir/docs/api-contract.md"
expect contract fail "$dir" not-in-code

dir=$(copy_repo docs-diagram)
perl -pi -e 's/forwarded once auth-service allows it/forwarded at once/' "$dir/docs/diagrams/request-path.svg"
expect docs fail "$dir" "request-path.svg differs from what scripts/check-docs.py draws"

dir=$(copy_repo docs-call)
perl -0pi -e 's/(\n  payment-service:\n    http: \[)/$1money-query-service, /' "$dir/deploy/envs/local/network-policy/calls.yaml"
expect docs fail "$dir" "money-query-service calls payment-service"

dir=$(copy_repo docs-port)
perl -pi -e 's/127\.0\.0\.1:8085:8080/127.0.0.1:8095:8080/' "$dir/infra/docker/compose.yaml"
expect docs fail "$dir" "payment-service: Host port says"

dir=$(copy_repo docs-memory)
perl -0pi -e 's/(\n  logging-consumer:\n(?:    .*\n)*?    mem_limit:) 384m/$1 256m/' "$dir/infra/docker/compose.yaml"
expect docs fail "$dir" "logging-consumer: Memory limit says"

dir=$(copy_repo english)
printf '\xed\x95\x9c\xea\xb8\x80\n' >"$dir/docs/planted-hangul.md"
expect english fail "$dir" planted-hangul.md

dir=$(copy_repo env-example)
echo 'DOCKER_HOST_IP=127.0.0.1' >>"$dir/infra/docker/.env.example"
expect env fail "$dir" DOCKER_HOST_IP

dir=$(copy_repo env-service)
perl -0pi -e 's/(\n      SERVICE_BANKING_URL: [^\n]*)/$1\n      ZOOKEEPER_CONNECT: zookeeper:2181/' "$dir/infra/docker/compose.yaml"
expect env fail "$dir" ZOOKEEPER_CONNECT

dir=$(copy_repo terraform)
printf 'locals {\n  selftest   = 1\n}\n' >>"$dir/infra/terraform/envs/dev/main.tf"
expect terraform fail "$dir" selftest

dir=$(copy_repo terraform-schema)
perl -0pi -e 's/\n\}\s*\z/,\n  "StreamSpecification": {"StreamEnabled": true, "StreamViewType": "NEW_IMAGE"}\n}\n/' "$dir/infra/terraform/modules/dynamodb-money-sum/table.json"
expect terraform fail "$dir" StreamSpecification

dir=$(copy_repo terraform-mapping)
perl -pi -e 's/^(\s*range_key\s*=).*/$1 null/' "$dir/infra/terraform/modules/dynamodb-money-sum/main.tf"
expect terraform fail "$dir" "differs from table.json"

dir=$(copy_repo actionlint)
printf 'on: push\njobs:\n  broken:\n    steps:\n      - run: "true"\n' >"$dir/.github/workflows/broken.yml"
expect actionlint fail "$dir" broken.yml

dir=$(copy_repo charts-schema)
perl -pi -e 's/containerPort:/containerPorts:/' "$dir/deploy/charts/spring-service/templates/deployment.yaml"
expect charts fail "$dir" containerPorts

dir=$(copy_repo charts-tag)
perl -pi -e 's/^(\s*tag:).*/$1 latest/; s/^(\s*pullPolicy:).*/$1 IfNotPresent/' "$dir/deploy/envs/local/frontend.yaml"
expect charts fail "$dir" "'latest' does not match pattern"

dir=$(copy_repo charts-unpulled)
cp -R "$dir/deploy/envs/local" "$dir/deploy/envs/staging"
expect charts fail "$dir" "staging: frontend-web uses imagePullPolicy Never"

dir=$(copy_repo charts-dependency)
perl -pi -e 's/^image: postgres:19\.0/image: postgres:18.0/' "$dir/deploy/envs/local/dependency/postgres.yaml"
expect charts fail "$dir" "postgres: image postgres:18.0"

dir=$(copy_repo charts-built-dependency)
perl -pi -e 's/^FROM otel\/opentelemetry-collector-contrib:0\.165\.0/FROM otel\/opentelemetry-collector-contrib:0.164.0/' "$dir/infra/docker/otel-collector/Dockerfile"
expect charts fail "$dir" "differs from otel/opentelemetry-collector-contrib:0.164.0"

dir=$(copy_repo charts-dependency-file)
rm "$dir/infra/docker/vault/encryption-key.sh"
expect charts fail "$dir" "files/vault/encryption-key.sh is empty or missing"

dir=$(copy_repo charts-workload)
rm "$dir/deploy/envs/local/spring-service/task-consumer.yaml"
expect charts fail "$dir" "no workload task-consumer"

dir=$(copy_repo charts-env)
perl -ni -e 'print unless /SERVICE_PAYMENT_URL/' "$dir/deploy/envs/local/spring-service/settlement-service.yaml"
expect charts fail "$dir" "passes SERVICE_PAYMENT_URL but deploy/envs/local does not"

dir=$(copy_repo charts-upstream)
perl -pi -e 's/(JAYPAY_AUTH_URL:).*/$1 auth-svc/' "$dir/deploy/envs/local/frontend/frontend-admin.yaml"
expect charts fail "$dir" "auth-svc.jaypay.svc.cluster.local:8080, which is no Service"

dir=$(copy_repo charts-policy-extra)
perl -0pi -e 's/(\n  auth-service:\n    http: \[)/$1frontend-web, /' "$dir/deploy/envs/local/network-policy/calls.yaml"
expect charts fail "$dir" "lets frontend-web reach auth-service on port http, but nothing"

dir=$(copy_repo charts-policy-missing)
perl -0pi -e 's/(\n  auth-service:\n    http: \[)frontend-admin, /$1/' "$dir/deploy/envs/local/network-policy/calls.yaml"
expect charts fail "$dir" "frontend-admin calls auth-service on port http, but no NetworkPolicy allows it"

dir=$(copy_repo charts-policy-default)
perl -pi -e 's/^  podSelector: \{\}$/  podSelector: {matchLabels: {app.kubernetes.io\/name: nothing}}/' "$dir/deploy/charts/network-policy/templates/network-policy.yaml"
expect charts fail "$dir" "no NetworkPolicy denies ingress to every pod by default"

dir=$(copy_repo charts-policy-port)
perl -pi -e 's/(SERVICE_MEMBERSHIP_URL: http:\/\/membership-service):8080/$1:9999/' "$dir/deploy/envs/local/spring-service/banking-service.yaml"
expect charts fail "$dir" "points at membership-service:9999, a port that the Service membership-service does not expose"

dir=$(copy_repo charts-secret)
printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: {{ .Release.Name }}\nstringData:\n  planted: "1"\n' >"$dir/deploy/charts/frontend/templates/secret.yaml"
expect charts fail "$dir" "renders the Secret frontend-web"

dir=$(copy_repo charts-applicationset)
perl -pi -e 's/^(\s*)selfHeal: true/$1selfHeal: true\n$1selfHealing: true/' "$dir/deploy/charts/gitops/templates/applicationset.yaml"
expect charts fail "$dir" "additional properties 'selfHealing' not allowed"

dir=$(copy_repo charts-argocd-version)
perl -pi -e 's/^ARGOCD_INSTALL_SHA256=.*/ARGOCD_INSTALL_SHA256=0000000000000000000000000000000000000000000000000000000000000000/' "$dir/scripts/verify.sh"
expect charts fail "$dir" "does not match ARGOCD_INSTALL_SHA256"

dir=$(copy_repo charts-argocd-patch)
perl -pi -e 's/name: argocd-repo-server$/name: argocd-repo-servers/' "$dir/deploy/argocd/local/kustomization.yaml"
expect charts fail "$dir" "argocd-repo-server does not mount jaypay-git"

[ "$failures" -eq 0 ] || { echo "$failures self-test case(s) failed"; exit 1; }
echo "verify.sh catches every planted failure"
