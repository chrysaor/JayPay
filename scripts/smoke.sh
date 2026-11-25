#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
compose_file="$root/infra/docker/compose.yaml"
env_file="$root/infra/docker/.env"
web=${SMOKE_WEB_URL:-http://localhost:8090}
admin=${SMOKE_ADMIN_URL:-http://localhost:8091}
clickhouse=${SMOKE_CLICKHOUSE_URL:-http://localhost:8123}
prometheus=${SMOKE_PROMETHEUS_URL:-http://localhost:9090}
stack=${SMOKE_STACK:-compose}
services=(
  membership-service banking-service money-service remittance-service payment-service
  money-aggregation-service money-query-service settlement-service auth-service task-consumer logging-consumer
)

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
: >"$work/last"
http_status=000
bearer=""

log() { echo "smoke: $*"; }
fail() { echo "smoke: FAILED: $*" >&2; exit 1; }
observed() { echo "$*" >"$work/last"; }
response() { { head -c 300 "$work/body" 2>/dev/null || true; cat "$work/curl-error"; } | tr -s '\r\n\t ' ' '; }

hex() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }

call() {
  local method=$1 url=$2 trace=$3 body=${4:-} type=${5:-application/json}
  local args=(-sS -o "$work/body" -w '%{http_code}' --max-time 90 -X "$method" -H "traceparent: 00-$trace-$(hex 8)-01")
  if [ -n "$body" ]; then
    args+=(-H "Content-Type: $type" --data-binary "$body")
  fi
  if [ -n "$bearer" ]; then
    args+=(-H "Authorization: Bearer $bearer")
  fi
  : >"$work/body"
  http_status=$(curl "${args[@]}" "$url" 2>"$work/curl-error") || true
}

request() {
  local what=$1
  shift
  call "$@"
  [ "$http_status" = 200 ] || fail "$what returned HTTP $http_status: $(response)"
}

expect() {
  local what=$1 expected=$2
  shift 2
  call "$@"
  [ "$http_status" = "$expected" ] || fail "$what returned HTTP $http_status instead of $expected: $(response)"
}

as() {
  bearer=$1
  shift
  "$@"
  bearer=""
}

json_field() {
  python3 - "$work/body" "$1" <<'PY' 2>/dev/null || true
import json, sys
value = json.load(open(sys.argv[1]))
for part in sys.argv[2].split("."):
    value = value[int(part)] if isinstance(value, list) else value.get(part)
print("" if value is None else value)
PY
}

json_has() {
  python3 - "$work/body" "$1" <<'PY' 2>/dev/null
import json, sys
sys.exit(0 if sys.argv[2] in json.load(open(sys.argv[1])) else 1)
PY
}

eventually() {
  local seconds=$1 what=$2
  shift 2
  local deadline=$(($(date +%s) + seconds))
  until "$@"; do
    [ "$(date +%s)" -lt "$deadline" ] || fail "$what after ${seconds}s ($(cat "$work/last"))"
    sleep 2
  done
}

clickhouse_query() {
  curl -sS --fail-with-body --max-time 30 -H @"$work/clickhouse-auth" --data-binary "$1" \
    "$clickhouse/?database=otel&default_format=TSV" 2>&1 || true
}

wallet_balance_is() {
  local member=$1 expected=$2 balance=""
  call POST "$web/api/money/money/member-money" "$(hex 16)" "{\"targetMembershipIds\":[\"$member\"]}"
  [ "$http_status" != 200 ] || balance=$(json_field 0.balance)
  observed "HTTP $http_status, balance ${balance:-none}: $(response)"
  [ "$balance" = "$expected" ]
}

bank_account_registered() {
  local aggregate=""
  call GET "$web/api/banking/banking/account/$1" "$(hex 16)"
  [ "$http_status" != 200 ] || aggregate=$(json_field aggregateIdentifier)
  observed "HTTP $http_status, aggregate ${aggregate:-none}: $(response)"
  [ -n "$aggregate" ]
}

region_total_is() {
  local expected=$1 total=""
  call GET "$admin/api/money-query/money/query/get-money-sum-by-address/$region" "$(hex 16)"
  [ "$http_status" != 200 ] || total=$(cat "$work/body")
  observed "HTTP $http_status, total ${total:-none}: $(response)"
  [ "$total" = "$expected" ]
}

cluster() { kubectl --context jaypay --namespace "${SMOKE_NAMESPACE:?scripts/k8s.sh sets it}" "$@"; }

recreate_dynamodb() {
  if [ "$stack" = kubernetes ]; then
    cluster delete pod dynamodb-0 >"$work/recreate" 2>&1 &&
      until cluster get pod dynamodb-0 >/dev/null 2>&1; do sleep 1; done &&
      cluster wait --for=condition=Ready pod/dynamodb-0 --timeout=120s >>"$work/recreate" 2>&1
  else
    docker compose -f "$compose_file" rm --stop --force dynamodb >"$work/recreate" 2>&1 &&
      docker compose -f "$compose_file" up --detach --wait dynamodb >>"$work/recreate" 2>&1
  fi || fail "could not recreate the dynamodb container: $(tail -5 "$work/recreate")"
}

service_logged_since_start() {
  local service=$1 container started count
  container=$(docker compose -f "$compose_file" ps -q "$service")
  [ -n "$container" ] || { observed "$service is not running"; return 1; }
  started=$(docker inspect -f '{{.State.StartedAt}}' "$container")
  count=$(clickhouse_query "SELECT count() FROM otel_logs WHERE ServiceName = '$service' AND Timestamp >= parseDateTime64BestEffort('$started', 9)")
  observed "$service has ${count:-no} log lines since it started at $started"
  [ "${count:-0}" -gt 0 ] 2>/dev/null
}

trace_spans() {
  local trace=$1 required=$2 found missing=""
  found=$(clickhouse_query "SELECT arrayStringConcat(arraySort(groupUniqArray(ServiceName)), ',') FROM otel_traces WHERE TraceId = '$trace'")
  for service in ${required//,/ }; do
    case ",$found," in
      *",$service,"*) ;;
      *) missing="$missing $service" ;;
    esac
  done
  observed "trace $trace spans [${found}], missing [${missing# }]"
  [ -z "$missing" ]
}

trace_has_logs() {
  local count
  count=$(clickhouse_query "SELECT count() FROM otel_logs WHERE TraceId = '$1'")
  observed "trace $1 has ${count:-no} log lines"
  [ "${count:-0}" -gt 0 ] 2>/dev/null
}

targets_up() {
  curl -sS --max-time 10 "$prometheus/api/v1/targets?state=active" >"$work/targets" 2>"$work/curl-error" || { observed "Prometheus: $(cat "$work/curl-error")"; return 1; }
  local report
  if report=$(python3 - "$work/targets" "${#services[@]}" <<'PY'
import json, sys
targets = json.load(open(sys.argv[1]))["data"]["activeTargets"]
down = sorted(f"{t['labels'].get('service', t['scrapeUrl'])} ({t['lastError'] or t['health']})" for t in targets if t["health"] != "up")
print(f"{len(targets) - len(down)} of {len(targets)} targets up, expected {sys.argv[2]}" + (f", down: {', '.join(down)}" if down else ""))
sys.exit(0 if not down and len(targets) == int(sys.argv[2]) else 1)
PY
  ); then
    observed "$report"
    return 0
  fi
  observed "$report"
  return 1
}

case "$stack" in
  compose | kubernetes) ;;
  *) fail "SMOKE_STACK is $stack, use compose or kubernetes" ;;
esac
[ -f "$env_file" ] || fail "$env_file is missing, run scripts/init-env.py first"
clickhouse_password=$(sed -n 's/^CLICKHOUSE_PASSWORD=//p' "$env_file")
umask 077
printf 'X-ClickHouse-User: otel\nX-ClickHouse-Key: %s\n' "$clickhouse_password" >"$work/clickhouse-auth"
run=$(date +%s)
region="Smoke-$run"

log "both sites answer"
for site in "$web" "$admin"; do
  request "GET $site/" GET "$site/" "$(hex 16)"
done

sign_up() {
  local name=$1 email=$2
  request "sign up of $name" POST "$web/api/membership/memberships" "$(hex 16)" \
    '{"name":"'"$name"'","email":"'"$email"'","address":"'"$region"'","corp":false}'
  signed_up=$(json_field membershipId)
  [ -n "$signed_up" ] || fail "sign up of $name answered without a membershipId: $(response)"
  ! json_has refreshToken || fail "sign up of $name answered with a refresh token: $(response)"
}

open_wallet() {
  request "wallet creation for member $1" POST "$web/api/money/money/create-member-money" "$(hex 16)" '{"membershipId":"'"$1"'"}'
  eventually 60 "the wallet of member $1 never appeared" wallet_balance_is "$1" 0
}

log "sign up through the web site"
sign_up "Smoke Tester" "smoke-$run@example.com"
membership_id=$signed_up
log "member $membership_id"

log "register a bank account"
account='{"membershipId":"'$membership_id'","bankName":"Jay Bank","bankAccountNumber":"1002-'$run'","valid":true}'
request "bank account registration" POST "$web/api/banking/banking/account/register-eda" "$(hex 16)" "$account"
eventually 60 "the bank account never got an aggregate" bank_account_registered "$membership_id"

log "open a wallet"
open_wallet "$membership_id"

log "top up 10000 through the Axon saga"
saga_trace=$(hex 16)
request "saga top-up" POST "$web/api/money/money/increase-eda" "$saga_trace" '{"targetMembershipId":"'$membership_id'","amount":10000}'
eventually 90 "the saga top-up never reached the wallet" wallet_balance_is "$membership_id" 10000

log "top up 5000 through the Kafka task"
task_trace=$(hex 16)
request "Kafka task top-up" POST "$web/api/money/money/increase-async" "$task_trace" '{"targetMembershipId":"'$membership_id'","amount":5000}'
eventually 60 "the Kafka task top-up never reached the wallet" wallet_balance_is "$membership_id" 15000

log "top up 2000 directly, the way the wallet page does"
request "direct top-up" POST "$web/api/money/money/increase" "$(hex 16)" '{"targetMembershipId":"'$membership_id'","amount":2000}'
[ -n "$(json_field moneyChangingRequestId)" ] || fail "direct top-up returned $(response)"
eventually 30 "the direct top-up never reached the wallet" wallet_balance_is "$membership_id" 17000

log "send 5000 to another member"
sign_up "Smoke Receiver" "smoke-receiver-$run@example.com"
receiver=$signed_up
open_wallet "$receiver"
request "remittance" POST "$web/api/remittance/remittance/request" "$(hex 16)" \
  '{"fromMembershipId":"'$membership_id'","toMembershipId":"'$receiver'","remittanceType":0,"amount":5000}'
[ "$(json_field remittanceStatus)" = success ] || fail "remittance returned $(response)"
eventually 30 "the remittance never left the sender's wallet" wallet_balance_is "$membership_id" 12000
eventually 30 "the remittance never reached the receiver's wallet" wallet_balance_is "$receiver" 5000

log "register a second member's bank account synchronously, the way the web site does, and top it up through the saga"
sign_up "Smoke Sync" "smoke-sync-$run@example.com"
sync_member=$signed_up
request "synchronous bank account registration" POST "$web/api/banking/banking/account/register" "$(hex 16)" \
  '{"membershipId":"'$sync_member'","bankName":"Jay Bank","bankAccountNumber":"2002-'$run'","valid":true}'
[ -n "$(json_field aggregateIdentifier)" ] || fail "synchronous registration stored no aggregate: $(response)"
open_wallet "$sync_member"
request "saga top-up of the synchronously registered account" POST "$web/api/money/money/increase-eda" "$(hex 16)" \
  '{"targetMembershipId":"'$sync_member'","amount":4000}'
eventually 90 "the saga top-up of the synchronously registered account never reached the wallet" wallet_balance_is "$sync_member" 4000

log "pay a merchant"
payment_trace=$(hex 16)
request "payment" POST "$web/api/payment/payment/request" "$payment_trace" \
  "requestMembershipId=$membership_id&requestPrice=3000&franchiseId=1&franchiseFeeRate=3" application/x-www-form-urlencoded
payment_id=$(json_field paymentId)
[ -n "$payment_id" ] || fail "payment returned $(response)"

staff_setting() { sed -n "s/^STAFF_$1=//p" "$env_file"; }

sign_in_staff() {
  call POST "$admin/api/auth/staff/sign-in" "$(hex 16)" \
    "$(python3 -c 'import json, sys; print(json.dumps({"username": sys.argv[1], "password": sys.argv[2]}))' "$1" "$2")"
}

sign_in_default_staff() {
  sign_in_staff "$(staff_setting "$1_USERNAME")" "$(staff_setting "$1_PASSWORD")"
  [ "$http_status" = 200 ] || fail "sign in of the default $1 account returned HTTP $http_status: $(response)"
  [ "$(json_field account.role)" = "$1" ] || fail "the default $1 account signed in as $(response)"
  signed_in=$(json_field token)
}

log "staff sign in to the admin console with the default accounts"
sign_in_default_staff ADMIN
admin_token=$signed_in
sign_in_default_staff OPERATOR
operator_token=$signed_in
sign_in_default_staff VIEWER
viewer_token=$signed_in

log "the admin console turns away a request without a staff token"
member_url="$admin/api/membership/memberships/$membership_id"
expect "admin member lookup without a token" 401 GET "$member_url" "$(hex 16)"
[ "$(json_field error)" = UNAUTHENTICATED ] || fail "admin member lookup without a token answered $(response)"
request "customer sign in" POST "$web/api/membership/memberships/login" "$(hex 16)" '{"membershipId":"'$membership_id'"}'
customer_token=$(json_field jwtToken)
[ -n "$customer_token" ] || fail "customer sign in returned no token: $(response)"
as "$customer_token" expect "admin member lookup with a customer token" 401 GET "$member_url" "$(hex 16)"

log "a viewer reads every screen but writes nothing"
gate_trace=$(hex 16)
as "$viewer_token" request "admin member lookup by a viewer" GET "$member_url" "$gate_trace"
[ "$(json_field membershipId)" = "$membership_id" ] || fail "admin member lookup returned $(response)"
! json_has refreshToken || fail "admin member lookup answered with a refresh token: $(response)"
member=$(cat "$work/body")
as "$viewer_token" request "settlement list for a viewer" GET "$admin/api/payment/payment/normal-status" "$(hex 16)"
as "$viewer_token" expect "member edit by a viewer" 403 PUT "$member_url" "$(hex 16)" "$member"
[ "$(json_field error)" = FORBIDDEN ] || fail "member edit by a viewer answered $(response)"
as "$viewer_token" expect "Mark settled by a viewer" 403 POST "$admin/api/payment/payment/finish-settlement" "$(hex 16)" '{"paymentId":"'$payment_id'"}'

log "a viewer reads the saga top-ups of region $region, and the total survives a new DynamoDB container"
as "$viewer_token" eventually 60 "the region total never reached 14000" region_total_is 14000
recreate_dynamodb
as "$viewer_token" eventually 30 "the region total did not survive a new DynamoDB container" region_total_is 14000

log "an operator edits a member but cannot manage staff accounts"
as "$operator_token" request "member edit by an operator" PUT "$member_url" "$(hex 16)" "$member"
as "$operator_token" expect "staff account list for an operator" 403 GET "$admin/api/auth/staff/accounts" "$(hex 16)"
as "$operator_token" expect "staff account creation by an operator" 403 POST "$admin/api/auth/staff/accounts" "$(hex 16)" \
  '{"username":"smoke-operator-'$run'","password":"Smoke-Operator-'$run'!","role":"ADMIN","mfaEnabled":false}'

log "an admin creates a staff account, and once deactivated it can no longer sign in"
new_staff="smoke-$run"
new_password="Smoke-$(hex 8)-Pass-42!"
as "$admin_token" expect "staff account with a weak password" 400 POST "$admin/api/auth/staff/accounts" "$(hex 16)" \
  '{"username":"'$new_staff'","password":"weak","role":"OPERATOR","mfaEnabled":false}'
[ "$(json_field violations.0.rule)" = MIN_LENGTH ] || fail "the weak password was not refused for its length: $(response)"
as "$admin_token" request "staff account creation by an admin" POST "$admin/api/auth/staff/accounts" "$(hex 16)" \
  '{"username":"'$new_staff'","password":"'$new_password'","role":"OPERATOR","mfaEnabled":false}'
sign_in_staff "$new_staff" "$new_password"
[ "$http_status" = 200 ] || fail "sign in of the new staff account returned HTTP $http_status: $(response)"
new_token=$(json_field token)
as "$new_token" request "member lookup by the new staff account" GET "$member_url" "$(hex 16)"
as "$admin_token" request "deactivation of the new staff account" PUT "$admin/api/auth/staff/accounts/$new_staff" "$(hex 16)" \
  '{"role":"OPERATOR","active":false,"mfaEnabled":false}'
sign_in_staff "$new_staff" "$new_password"
[ "$http_status" = 401 ] || fail "sign in of the deactivated staff account returned HTTP $http_status: $(response)"
as "$new_token" expect "member lookup with the token of a deactivated account" 401 GET "$member_url" "$(hex 16)"

log "the gate refuses what the admin console never asks for"
as "$admin_token" expect "payment request through the admin console" 403 POST "$admin/api/payment/payment/request" "$(hex 16)" \
  "requestMembershipId=$membership_id&requestPrice=1&franchiseId=1&franchiseFeeRate=3" application/x-www-form-urlencoded
as "$admin_token" expect "a path that climbs out of its segment" 403 GET "$member_url/..%2F..%2Fregister" "$(hex 16)"
as "$admin_token" expect "the actuator of a service behind the console" 403 GET "$admin/api/membership/actuator/health" "$(hex 16)"
expect "the actuator of the auth service through the console" 404 GET "$admin/api/auth/actuator/health" "$(hex 16)"

log "a staff token means nothing to the customer site"
call POST "$web/api/membership/memberships/token-validate" "$(hex 16)" '{"jwtToken":"'$admin_token'"}'
[ "$(cat "$work/body")" != true ] || fail "the web site accepted a staff token: $(response)"

if [ "$stack" = kubernetes ]; then
  log "PASSED, without the log, trace and scrape checks, because the cluster runs no collector and no Prometheus yet"
  exit 0
fi

log "every service shipped its logs to ClickHouse"
for service in "${services[@]}"; do
  eventually 60 "no logs from $service in ClickHouse" service_logged_since_start "$service"
done

log "traces cross the service boundaries and lead to their logs"
for check in \
  "HTTP:$payment_trace:payment-service,membership-service" \
  "Gate:$gate_trace:auth-service,membership-service" \
  "Kafka:$task_trace:money-service,task-consumer" \
  "Axon:$saga_trace:money-service,banking-service"; do
  IFS=: read -r boundary trace required <<<"$check"
  eventually 60 "the $boundary trace does not span $required" trace_spans "$trace" "$required"
  eventually 30 "no logs carry the $boundary trace id" trace_has_logs "$trace"
  log "$boundary trace $trace spans $required"
done

log "Prometheus scrapes every service"
eventually 60 "Prometheus does not see every service up" targets_up
log "$(cat "$work/last")"

log "PASSED"
