#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
mkdir -p "$root/build"
work=$(mktemp -d "$root/build/verify-selftest.XXXXXX")
trap 'rm -rf "$work"' EXIT
failures=0

copy_repo() {
  local dest="$work/$1"
  mkdir -p "$dest"
  git -C "$root" ls-files -co --exclude-standard -- scripts docs/api-contract.md backend deploy infra/terraform infra/docker frontend/Dockerfile frontend/apps .github |
    while IFS= read -r path; do
      [ -e "$root/$path" ] || continue
      mkdir -p "$dest/$(dirname "$path")"
      cp "$root/$path" "$dest/$path"
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
expect actionlint pass "$clean"
expect english pass "$clean"
expect env pass "$clean"
expect charts pass "$clean"

dir=$(copy_repo contract)
echo '| banking-service | GET | /banking/not-in-code |' >>"$dir/docs/api-contract.md"
expect contract fail "$dir" not-in-code

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
perl -pi -e 's/^(\s*tag:).*/$1 latest/' "$dir/deploy/envs/local/frontend.yaml"
expect charts fail "$dir" "'latest' does not match pattern"

dir=$(copy_repo charts-workload)
rm "$dir/deploy/envs/local/spring-service/task-consumer.yaml"
expect charts fail "$dir" "no workload task-consumer"

dir=$(copy_repo charts-env)
perl -ni -e 'print unless /SERVICE_PAYMENT_URL/' "$dir/deploy/envs/local/spring-service/settlement-service.yaml"
expect charts fail "$dir" "passes SERVICE_PAYMENT_URL but deploy/envs/local does not"

dir=$(copy_repo charts-upstream)
perl -pi -e 's/(JAYPAY_AUTH_URL:).*/$1 auth-svc/' "$dir/deploy/envs/local/frontend/frontend-admin.yaml"
expect charts fail "$dir" "auth-svc.jaypay.svc.cluster.local:8080, which is no Service"

[ "$failures" -eq 0 ] || { echo "$failures self-test case(s) failed"; exit 1; }
echo "verify.sh catches every planted failure"
