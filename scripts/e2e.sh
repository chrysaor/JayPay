#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
env_file="$root/infra/docker/.env"

staff_setting() { sed -n "s/^STAFF_$1=//p" "$env_file"; }

[ -f "$env_file" ] || { echo "e2e: FAILED: $env_file is missing, see infra/docker/.env.example" >&2; exit 1; }
curl -fsS -o /dev/null --max-time 10 "${E2E_WEB_URL:-http://localhost:8090}/" ||
  { echo "e2e: FAILED: the web site does not answer, run make up first" >&2; exit 1; }

cd "$root/frontend"
npx --no-install playwright install ${CI:+--with-deps} chromium
E2E_STAFF_USERNAME=$(staff_setting VIEWER_USERNAME) E2E_STAFF_PASSWORD=$(staff_setting VIEWER_PASSWORD) \
  npx --no-install playwright test --config e2e/playwright.config.ts "$@"
