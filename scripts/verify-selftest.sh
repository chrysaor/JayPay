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
  git -C "$root" ls-files -co --exclude-standard -- scripts docs/api-contract.md backend infra/terraform .github |
    while IFS= read -r path; do
      [ -e "$root/$path" ] || continue
      mkdir -p "$dest/$(dirname "$path")"
      cp "$root/$path" "$dest/$path"
    done
  echo "$dest"
}

expect() {
  local name=$1 want=$2 dir=$3
  shift 3
  local out status=0
  out=$("$dir/scripts/verify.sh" "$@" 2>&1) || status=$?
  if [ "$want" = pass ] && [ "$status" -eq 0 ]; then
    echo "ok   $name"
  elif [ "$want" = fail ] && [ "$status" -ne 0 ] && grep -Eq "^FAILED:( .*)? $name( |$)" <<<"$out"; then
    echo "ok   $name"
  else
    echo "FAIL $name (exit $status, wanted $want)"
    echo "$out" | tail -20
    failures=$((failures + 1))
  fi
}

clean=$(copy_repo clean)
expect contract pass "$clean" contract
expect actionlint pass "$clean" actionlint

dir=$(copy_repo contract)
echo '| banking-service | GET | `/banking/not-in-code` |' >>"$dir/docs/api-contract.md"
expect contract fail "$dir" contract

dir=$(copy_repo terraform)
printf 'locals {\n  selftest   = 1\n}\n' >>"$dir/infra/terraform/envs/dev/main.tf"
expect terraform fail "$dir" terraform

dir=$(copy_repo actionlint)
printf 'on: push\njobs:\n  broken:\n    steps:\n      - run: "true"\n' >"$dir/.github/workflows/broken.yml"
expect actionlint fail "$dir" actionlint

[ "$failures" -eq 0 ] || { echo "$failures self-test case(s) failed"; exit 1; }
echo "verify.sh catches every planted failure"
