#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
compose_file="$root/infra/docker/compose.yaml"
env_file="$root/infra/docker/.env"
web=${SMOKE_WEB_URL:-http://localhost:8090}
admin=${SMOKE_ADMIN_URL:-http://localhost:8091}
keycloak=${SMOKE_KEYCLOAK_URL:-http://localhost:8092}
banking=${SMOKE_BANKING_URL:-http://localhost:8082}
money=${SMOKE_MONEY_URL:-http://localhost:8083}
clickhouse=${SMOKE_CLICKHOUSE_URL:-http://localhost:8123}
prometheus=${SMOKE_PROMETHEUS_URL:-http://localhost:9090}
grafana=${SMOKE_GRAFANA_URL:-http://localhost:3030}
vault=${SMOKE_VAULT_URL:-http://localhost:8200}
read -r -a service_urls <<<"${SMOKE_SERVICE_URLS:-$(printf 'http://localhost:%s ' 8081 8082 8083 8084 8085 8086 8087 8088 8089 8093)}"
management_port=9080
stack=${SMOKE_STACK:-compose}
services=(
  membership-service banking-service money-service remittance-service payment-service
  money-aggregation-service money-query-service settlement-service event-flow-service auth-service logging-consumer
)

work=$(mktemp -d)
smoke_users=()
remove_smoke_users() {
  local id
  if [ ${#smoke_users[@]} -gt 0 ] && (keycloak_admin_sign_in) >/dev/null 2>&1; then
    for id in "${smoke_users[@]}"; do
      curl -sS -o /dev/null --max-time 30 -X DELETE -H "Authorization: Bearer $(cat "$work/keycloak-admin")" \
        "$keycloak/admin/realms/staff/users/$id" || true
    done
  fi
  rm -rf "$work"
}
trap remove_smoke_users EXIT
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
  local args=(-sS --path-as-is -o "$work/body" -w '%{http_code}' --max-time 90 -X "$method" -H "traceparent: 00-$trace-$(hex 8)-01")
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

wallets_total_is() {
  local expected=$1 total=""
  shift
  call POST "$web/api/money/money/member-money" "$(hex 16)" \
    "$(python3 -c 'import json, sys; print(json.dumps({"targetMembershipIds": sys.argv[1:]}))' "$@")"
  [ "$http_status" != 200 ] || total=$(python3 -c 'import json, sys; print(sum(w["balance"] for w in json.load(open(sys.argv[1]))))' "$work/body" 2>/dev/null)
  observed "HTTP $http_status, total ${total:-none} of $*: $(response)"
  [ "$total" = "$expected" ]
}

bank_account_registered() {
  local aggregate=""
  call GET "$web/api/banking/banking/account/$1" "$(hex 16)"
  [ "$http_status" != 200 ] || aggregate=$(json_field aggregateIdentifier)
  observed "HTTP $http_status, aggregate ${aggregate:-none}: $(response)"
  [ -n "$aggregate" ]
}

payment_settled() {
  call GET "$admin/api/payment/payment/normal-status" "$(hex 16)"
  observed "HTTP $http_status: $(response)"
  [ "$http_status" = 200 ] && python3 -c 'import json, sys; sys.exit(0 if all(p["paymentId"] != int(sys.argv[2]) for p in json.load(open(sys.argv[1]))) else 1)' "$work/body" "$1"
}

region_total_is() {
  local expected=$1 total=""
  call GET "$admin/api/money-query/money/query/get-money-sum-by-address/$region" "$(hex 16)"
  [ "$http_status" != 200 ] || total=$(cat "$work/body")
  observed "HTTP $http_status, total ${total:-none}: $(response)"
  [ "$total" = "$expected" ]
}

flow_is() {
  local type=$1 flow=$2 status=$3 failed=$4 steps=$5
  call GET "$admin/api/event-flow/flows/$type/$flow" "$(hex 16)"
  observed "HTTP $http_status: $(response)"
  [ "$http_status" = 200 ] && python3 - "$work/body" "$status" "$failed" "$steps" <<'PY'
import json, sys
flow = json.load(open(sys.argv[1]))
steps = [step["step"] for step in flow["steps"]]
failed = [step["step"] for step in flow["steps"] if step["outcome"] == "FAILED"]
sys.exit(0 if flow["status"] == sys.argv[2] and (flow["failedStep"] or "") == sys.argv[3]
         and steps == sys.argv[4].split() and failed[:1] == sys.argv[3].split() else 1)
PY
}

cluster() { kubectl --context jaypay --namespace "${SMOKE_NAMESPACE:?scripts/k8s.sh sets it}" "$@"; }

pod_exists() { cluster get pod "$1" >"$work/last" 2>&1; }

recreate_dynamodb() {
  if [ "$stack" = kubernetes ]; then
    cluster delete pod dynamodb-0 >"$work/recreate" 2>&1 &&
      eventually 60 "the pod dynamodb-0 did not come back" pod_exists dynamodb-0 &&
      cluster wait --for=condition=Ready pod/dynamodb-0 --timeout=120s >>"$work/recreate" 2>&1
  else
    docker compose -f "$compose_file" rm --stop --force dynamodb >"$work/recreate" 2>&1 &&
      docker compose -f "$compose_file" up --detach --wait dynamodb >>"$work/recreate" 2>&1
  fi || fail "could not recreate the dynamodb container: $(tail -5 "$work/recreate")"
}

postgres_memory_percent() {
  local cgroup='cat /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.max && sed -n "s/^inactive_file //p" /sys/fs/cgroup/memory.stat'
  if [ "$stack" = kubernetes ]; then
    cluster exec postgres-0 -- sh -c "$cgroup"
  else
    docker exec "$(docker compose -f "$compose_file" ps -q postgres)" sh -c "$cgroup"
  fi | awk '{ v[NR] = $1 } END { if (NR != 3 || v[2] !~ /^[0-9]+$/) exit 1; printf "%d\n", (v[1] - v[3]) * 100 / v[2] }'
}

started_at() {
  local container
  if [ "$stack" = kubernetes ]; then
    cluster get pods --selector "app.kubernetes.io/name=$1" \
      --output jsonpath='{.items[0].status.containerStatuses[0].state.running.startedAt}' 2>/dev/null
  else
    container=$(docker compose -f "$compose_file" ps -q "$1")
    [ -z "$container" ] || docker inspect -f '{{.State.StartedAt}}' "$container"
  fi
}

service_logged_since_start() {
  local service=$1 started count
  started=$(started_at "$service")
  [ -n "$started" ] || { observed "$service is not running"; return 1; }
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

as_grafana_reader() {
  curl -sS -o "$work/body" -w '%{http_code}' --max-time 30 -H @"$work/clickhouse-grafana-auth" --data-binary "$1" \
    "$clickhouse/?database=otel&default_format=TSV" 2>"$work/curl-error" || true
}

as_membership_vault() {
  local method=$1 path=$2 body=${3:-}
  local args=(-sS -o "$work/body" -w '%{http_code}' --max-time 30 -X "$method" -H @"$work/vault-membership-auth")
  [ -z "$body" ] || args+=(-H "Content-Type: application/json" --data-binary "$body")
  curl "${args[@]}" "$vault/v1/$path" 2>"$work/curl-error" || true
}

root_containers() {
  if [ "$stack" = kubernetes ]; then
    cluster get pods --output json | python3 -c '
import json, sys
for pod in json.load(sys.stdin)["items"]:
    spec = pod["spec"]
    for container in spec.get("initContainers", []) + spec["containers"]:
        context = {**(spec.get("securityContext") or {}), **(container.get("securityContext") or {})}
        if context.get("runAsNonRoot") is not True or not context.get("runAsUser"):
            print(pod["metadata"]["name"] + "/" + container["name"])
'
  else
    docker compose -f "$compose_file" ps --quiet | xargs docker inspect --format '{{.Name}} {{.Config.User}}' |
      awk '{ split($2, id, ":"); if (id[1] == "" || id[1] == "0" || id[1] == "root") print substr($1, 2) }'
  fi
}

wide_ports() {
  if [ "$stack" = kubernetes ]; then
    cluster get services --output jsonpath='{range .items[?(@.spec.type!="ClusterIP")]}{.metadata.name} {.spec.type}{"\n"}{end}'
  else
    docker compose -f "$compose_file" ps --format '{{.Name}} {{.Ports}}' | tr ',' '\n' |
      awk '/->/ && !/127\.0\.0\.1:[0-9-]+->/'
  fi
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
  if report=$(python3 - "$work/targets" "${#services[@]}" "$management_port" <<'PY'
import json, sys
from urllib.parse import urlsplit
targets = json.load(open(sys.argv[1]))["data"]["activeTargets"]
down = sorted(f"{t['labels'].get('service', t['scrapeUrl'])} ({t['lastError'] or t['health']})" for t in targets if t["health"] != "up")
elsewhere = sorted(t["scrapeUrl"] for t in targets if urlsplit(t["scrapeUrl"]).port != int(sys.argv[3]))
print(f"{len(targets) - len(down)} of {len(targets)} targets up on port {sys.argv[3]}, expected {sys.argv[2]}"
      + (f", down: {', '.join(down)}" if down else "") + (f", scraped elsewhere: {', '.join(elsewhere)}" if elsewhere else ""))
sys.exit(0 if not down and not elsewhere and len(targets) == int(sys.argv[2]) else 1)
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
printf 'X-ClickHouse-User: grafana\nX-ClickHouse-Key: %s\n' "$(sed -n 's/^CLICKHOUSE_GRAFANA_PASSWORD=//p' "$env_file")" >"$work/clickhouse-grafana-auth"
printf 'X-Vault-Token: %s\n' "$(sed -n 's/^VAULT_MEMBERSHIP_TOKEN=//p' "$env_file")" >"$work/vault-membership-auth"
run=$(date +%s)
region="Smoke-$run"

log "both sites answer"
for site in "$web" "$admin"; do
  request "GET $site/" GET "$site/" "$(hex 16)"
done

sign_up() {
  local name=$1 email=$2 corp=${3:-false}
  request "sign up of $name" POST "$web/api/membership/memberships" "$(hex 16)" \
    '{"name":"'"$name"'","email":"'"$email"'","address":"'"$region"'","corp":'"$corp"'}'
  signed_up=$(json_field membershipId)
  [ -n "$signed_up" ] || fail "sign up of $name answered without a membershipId: $(response)"
  ! json_has refreshToken || fail "sign up of $name answered with a refresh token: $(response)"
}

open_wallet() {
  request "wallet creation for member $1" POST "$money/money/create-member-money" "$(hex 16)" '{"membershipId":"'"$1"'"}'
  eventually 60 "the wallet of member $1 never appeared" wallet_balance_is "$1" 0
}

log "sign up through the web site"
sign_up "Smoke Tester" "smoke-$run@example.com"
membership_id=$signed_up
log "member $membership_id"

log "register a bank account"
account='{"membershipId":"'$membership_id'","bankName":"Jay Bank","bankAccountNumber":"1002-'$run'","valid":true}'
request "bank account registration" POST "$banking/banking/account/register-eda" "$(hex 16)" "$account"
eventually 60 "the bank account never got an aggregate" bank_account_registered "$membership_id"

log "open a wallet"
open_wallet "$membership_id"

log "top up 10000 through the Axon saga"
saga_trace=$(hex 16)
saga_top_up='{"requestId":"smoke-saga-'$run'","targetMembershipId":"'$membership_id'","amount":10000}'
request "saga top-up" POST "$money/money/increase-eda" "$saga_trace" "$saga_top_up"
eventually 90 "the saga top-up never reached the wallet" wallet_balance_is "$membership_id" 10000
request "repeated saga top-up" POST "$money/money/increase-eda" "$(hex 16)" "$saga_top_up"

log "top up 5000 more through the Axon saga"
kafka_trace=$(hex 16)
request "second saga top-up" POST "$money/money/increase-eda" "$kafka_trace" '{"requestId":"smoke-saga-2-'$run'","targetMembershipId":"'$membership_id'","amount":5000}'
eventually 90 "the second saga top-up never reached the wallet" wallet_balance_is "$membership_id" 15000

log "top up 2000 directly, the way the wallet page does"
direct_top_up='{"requestId":"smoke-direct-'$run'","targetMembershipId":"'$membership_id'","amount":2000}'
request "direct top-up" POST "$web/api/money/money/increase" "$(hex 16)" "$direct_top_up"
[ "$(json_field moneyChangingResultStatus)" = 1 ] || fail "direct top-up returned $(response)"
first_result=$(cat "$work/body")
eventually 30 "the direct top-up never reached the wallet" wallet_balance_is "$membership_id" 17000

log "a repeated top-up answers with the first result and changes nothing"
request "repeated direct top-up" POST "$web/api/money/money/increase" "$(hex 16)" "$direct_top_up"
[ "$(cat "$work/body")" = "$first_result" ] || fail "the repeated top-up answered $(response) instead of $first_result"
expect "a reused request id for another amount" 409 POST "$web/api/money/money/increase" "$(hex 16)" \
  '{"requestId":"smoke-direct-'$run'","targetMembershipId":"'$membership_id'","amount":3000}'
wallet_balance_is "$membership_id" 17000 || fail "a repeated top-up changed the wallet: $(cat "$work/last")"

log "money-service refuses amounts that are not positive or above the limit, and a decrease beyond the balance"
for amount in -1000 0 10000001 1.5; do
  expect "a top-up of $amount" 400 POST "$web/api/money/money/increase" "$(hex 16)" \
    '{"requestId":"smoke-bad-'$(hex 8)'","targetMembershipId":"'$membership_id'","amount":'$amount'}'
  [ "$(json_field error)" = INVALID_AMOUNT ] || fail "a top-up of $amount answered $(response)"
done
request "decrease beyond the balance" POST "$money/money/decrease" "$(hex 16)" \
  '{"requestId":"smoke-overdraw-'$run'","targetMembershipId":"'$membership_id'","amount":17001}'
[ "$(json_field moneyChangingResultStatus)" = 2 ] || fail "a decrease beyond the balance answered $(response)"
wallet_balance_is "$membership_id" 17000 || fail "a refused change moved the wallet: $(cat "$work/last")"

log "send 5000 to another member"
sign_up "Smoke Receiver" "smoke-receiver-$run@example.com"
receiver=$signed_up
open_wallet "$receiver"
wallets_total_is 17000 "$membership_id" "$receiver" || fail "the two wallets did not hold 17000 before the remittance: $(cat "$work/last")"
remit() {
  call POST "$web/api/remittance/remittance/request" "$(hex 16)" \
    '{"requestId":"'"$1"'","fromMembershipId":"'"$2"'","toMembershipId":"'"$3"'","remittanceType":0,"amount":'"$4"'}'
}
remit "smoke-remit-$run" "$membership_id" "$receiver" 5000
[ "$http_status" = 200 ] || fail "remittance returned HTTP $http_status: $(response)"
[ "$(json_field remittanceStatus)" = COMPLETED ] || fail "remittance returned $(response)"
first_remittance=$(cat "$work/body")
wallet_balance_is "$membership_id" 12000 || fail "the remittance did not leave the sender's wallet: $(cat "$work/last")"
wallet_balance_is "$receiver" 5000 || fail "the remittance did not reach the receiver's wallet: $(cat "$work/last")"

log "a repeated remittance answers with the first result and moves nothing more"
remit "smoke-remit-$run" "$membership_id" "$receiver" 5000
[ "$(cat "$work/body")" = "$first_remittance" ] || fail "the repeated remittance answered HTTP $http_status $(response) instead of $first_remittance"
remit "smoke-remit-$run" "$membership_id" "$receiver" 5001
[ "$http_status" = 409 ] && [ "$(json_field error)" = REQUEST_ID_REUSED ] || fail "a reused remittance request id answered HTTP $http_status: $(response)"
wallets_total_is 17000 "$membership_id" "$receiver" || fail "a repeated remittance changed the total: $(cat "$work/last")"
wallet_balance_is "$receiver" 5000 || fail "a repeated remittance changed the receiver's wallet: $(cat "$work/last")"

log "a remittance above the balance fails and moves nothing, and bad remittances are refused"
remit "smoke-overdraw-remit-$run" "$membership_id" "$receiver" 12001
[ "$http_status" = 200 ] && [ "$(json_field remittanceStatus)" = FAILED ] || fail "a remittance above the balance answered HTTP $http_status: $(response)"
wallet_balance_is "$membership_id" 12000 || fail "a failed remittance moved the sender's wallet: $(cat "$work/last")"
wallets_total_is 17000 "$membership_id" "$receiver" || fail "a failed remittance changed the total: $(cat "$work/last")"
for amount in -1000 0 10000001 1.5; do
  remit "smoke-bad-$(hex 8)" "$membership_id" "$receiver" "$amount"
  [ "$http_status" = 400 ] && [ "$(json_field error)" = INVALID_AMOUNT ] || fail "a remittance of $amount answered HTTP $http_status: $(response)"
done
remit "smoke-self-$run" "$membership_id" "$membership_id" 1000
[ "$http_status" = 400 ] && [ "$(json_field error)" = INVALID_RECEIVER ] || fail "a remittance to the sender answered HTTP $http_status: $(response)"
wallet_balance_is "$membership_id" 12000 || fail "a refused remittance moved the sender's wallet: $(cat "$work/last")"

log "the receiver sends 1000 to a bank account, and it leaves the receiver's wallet"
request "bank remittance" POST "$web/api/remittance/remittance/request" "$(hex 16)" \
  '{"requestId":"smoke-bank-remit-'$run'","fromMembershipId":"'$receiver'","toBankName":"Jay Bank","toBankAccountNumber":"4002-'$run'","remittanceType":1,"amount":1000}'
[ "$(json_field remittanceStatus)" = COMPLETED ] || fail "the bank remittance returned $(response)"
first_bank_remittance=$(cat "$work/body")
wallet_balance_is "$receiver" 4000 || fail "the bank remittance did not leave the receiver's wallet: $(cat "$work/last")"
request "repeated bank remittance" POST "$web/api/remittance/remittance/request" "$(hex 16)" \
  '{"requestId":"smoke-bank-remit-'$run'","fromMembershipId":"'$receiver'","toBankName":"Jay Bank","toBankAccountNumber":"4002-'$run'","remittanceType":1,"amount":1000}'
[ "$(cat "$work/body")" = "$first_bank_remittance" ] || fail "the repeated bank remittance answered $(response) instead of $first_bank_remittance"
wallet_balance_is "$receiver" 4000 || fail "a repeated bank remittance moved the receiver's wallet: $(cat "$work/last")"

log "banking-service sends a transfer once per request id and answers a repeat with the first result"
bank_transfer='{"requestId":"smoke-transfer-'$run'","toBankName":"Jay Bank","toBankAccountNumber":"4002-'$run'","moneyAmount":700}'
request "bank transfer" POST "$banking/banking/firmbanking/request" "$(hex 16)" "$bank_transfer"
[ "$(json_field transferStatus)" = SUCCEEDED ] || fail "the bank transfer answered $(response)"
company_account=$(json_field fromBankAccountNumber)
[ -n "$company_account" ] || fail "the bank transfer names no company account: $(response)"
first_transfer=$(cat "$work/body")
request "repeated bank transfer" POST "$banking/banking/firmbanking/request" "$(hex 16)" "$bank_transfer"
[ "$(cat "$work/body")" = "$first_transfer" ] || fail "the repeated bank transfer answered $(response) instead of $first_transfer"
expect "a reused transfer request id for another amount" 409 POST "$banking/banking/firmbanking/request" "$(hex 16)" \
  '{"requestId":"smoke-transfer-'$run'","toBankName":"Jay Bank","toBankAccountNumber":"4002-'$run'","moneyAmount":701}'
[ "$(json_field error)" = REQUEST_ID_REUSED ] || fail "a reused transfer request id answered $(response)"
request "transfer to an account the bank refuses" POST "$banking/banking/firmbanking/request" "$(hex 16)" \
  '{"requestId":"smoke-refused-transfer-'$run'","toBankName":"Jay Bank","toBankAccountNumber":"4002-'$run'-refused","moneyAmount":700}'
[ "$(json_field transferStatus)" = FAILED ] || fail "a refused transfer answered $(response)"

log "register a second member's bank account synchronously, the way the web site does, and top it up through the saga"
sign_up "Smoke Sync" "smoke-sync-$run@example.com"
sync_member=$signed_up
request "synchronous bank account registration" POST "$web/api/banking/banking/account/register" "$(hex 16)" \
  '{"membershipId":"'$sync_member'","bankName":"Jay Bank","bankAccountNumber":"2002-'$run'","valid":true}'
[ -n "$(json_field aggregateIdentifier)" ] || fail "synchronous registration stored no aggregate: $(response)"
open_wallet "$sync_member"
request "saga top-up of the synchronously registered account" POST "$money/money/increase-eda" "$(hex 16)" \
  '{"requestId":"smoke-sync-'$run'","targetMembershipId":"'$sync_member'","amount":4000}'
eventually 90 "the saga top-up of the synchronously registered account never reached the wallet" wallet_balance_is "$sync_member" 4000

recharge_failed() {
  call POST "$money/money/increase-eda" "$(hex 16)" "$1"
  observed "HTTP $http_status: $(response)"
  [ "$http_status" = 200 ] && [ "$(json_field moneyChangingResultStatus)" = 2 ]
}

log "a saga top-up the bank refuses ends as FAILED and leaves the wallet and the region total alone"
sign_up "Smoke Refused" "smoke-refused-$run@example.com"
refused_member=$signed_up
request "bank account registration the bank will refuse" POST "$web/api/banking/banking/account/register" "$(hex 16)" \
  '{"membershipId":"'$refused_member'","bankName":"Jay Bank","bankAccountNumber":"5002-'$run'-refused","valid":true}'
open_wallet "$refused_member"
refused_top_up='{"requestId":"smoke-refused-'$run'","targetMembershipId":"'$refused_member'","amount":3000}'
request "saga top-up the bank refuses" POST "$money/money/increase-eda" "$(hex 16)" "$refused_top_up"
eventually 90 "the refused saga top-up never ended as FAILED" recharge_failed "$refused_top_up"
wallet_balance_is "$refused_member" 0 || fail "a refused saga top-up moved the wallet: $(cat "$work/last")"

log "a saga top-up of a member that membership-service does not know ends as FAILED and leaves the wallet alone"
unknown_member="9$run"
request "bank account registration of an unknown member" POST "$banking/banking/account/register-eda" "$(hex 16)" \
  '{"membershipId":"'$unknown_member'","bankName":"Jay Bank","bankAccountNumber":"6002-'$run'","valid":true}'
eventually 60 "the bank account of the unknown member never got an aggregate" bank_account_registered "$unknown_member"
unknown_top_up='{"requestId":"smoke-unknown-'$run'","targetMembershipId":"'$unknown_member'","amount":6000}'
request "saga top-up of an unknown member" POST "$money/money/increase-eda" "$(hex 16)" "$unknown_top_up"
eventually 90 "the saga top-up of the unknown member never ended as FAILED" recharge_failed "$unknown_top_up"
wallet_balance_is "$unknown_member" 0 || fail "a saga top-up of an unknown member moved its wallet: $(cat "$work/last")"

log "pay a corporate merchant 3000 from the wallet of 12000, at the server's fee of 3%"
sign_up "Smoke Merchant" "smoke-merchant-$run@example.com" true
merchant=$signed_up
request "merchant bank account registration" POST "$web/api/banking/banking/account/register" "$(hex 16)" \
  '{"membershipId":"'$merchant'","bankName":"Jay Bank","bankAccountNumber":"3002-'$run'","valid":true}'
payment_trace=$(hex 16)
payment='{"requestId":"smoke-pay-'$run'","requestMembershipId":"'$membership_id'","franchiseId":"'$merchant'","requestPrice":3000}'
request "payment" POST "$web/api/payment/payment/request" "$payment_trace" "$payment"
payment_id=$(json_field paymentId)
[ -n "$payment_id" ] || fail "payment returned $(response)"
[ "$(json_field paymentStatus)" = PAID ] || fail "payment was not paid: $(response)"
[ "$(json_field fee)/$(json_field payout)" = 90/2910 ] || fail "payment charged the wrong fee: $(response)"
first_payment=$(cat "$work/body")
eventually 30 "the payment never left the payer's wallet" wallet_balance_is "$membership_id" 9000

log "a repeated payment answers with the first result and takes nothing more"
request "repeated payment" POST "$web/api/payment/payment/request" "$(hex 16)" "$payment"
[ "$(cat "$work/body")" = "$first_payment" ] || fail "the repeated payment answered $(response) instead of $first_payment"
expect "a reused payment request id for another price" 409 POST "$web/api/payment/payment/request" "$(hex 16)" \
  '{"requestId":"smoke-pay-'$run'","requestMembershipId":"'$membership_id'","franchiseId":"'$merchant'","requestPrice":3001}'
wallet_balance_is "$membership_id" 9000 || fail "a repeated payment changed the wallet: $(cat "$work/last")"

log "a payment above the balance fails and keeps the balance, and bad payments are refused"
request "payment above the balance" POST "$web/api/payment/payment/request" "$(hex 16)" \
  '{"requestId":"smoke-overpay-'$run'","requestMembershipId":"'$membership_id'","franchiseId":"'$merchant'","requestPrice":9001}'
[ "$(json_field paymentStatus)" = FAILED ] || fail "a payment above the balance answered $(response)"
wallet_balance_is "$membership_id" 9000 || fail "a failed payment moved the wallet: $(cat "$work/last")"
for amount in -1000 0 10000001 1.5; do
  expect "a payment of $amount" 400 POST "$web/api/payment/payment/request" "$(hex 16)" \
    '{"requestId":"smoke-bad-'$(hex 8)'","requestMembershipId":"'$membership_id'","franchiseId":"'$merchant'","requestPrice":'$amount'}'
  [ "$(json_field error)" = INVALID_AMOUNT ] || fail "a payment of $amount answered $(response)"
done
expect "a payment to a member that is not corporate" 400 POST "$web/api/payment/payment/request" "$(hex 16)" \
  '{"requestId":"smoke-personal-'$run'","requestMembershipId":"'$membership_id'","franchiseId":"'$receiver'","requestPrice":1000}'
[ "$(json_field error)" = INVALID_MERCHANT ] || fail "a payment to a personal member answered $(response)"

staff_setting() { sed -n "s/^STAFF_$1=//p" "$env_file"; }

sign_in_staff() {
  python3 "$root/scripts/staff_sign_in.py" "$admin" "$1" "$2" >"$work/staff-tokens" 2>"$work/staff-refusal"
}

staff_sign_in_refused() {
  local status=0
  sign_in_staff "$1" "$2" || status=$?
  [ "$status" = 3 ] || fail "$3, the sign in exited $status: $(cat "$work/staff-refusal")"
}

staff_token() { python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$work/staff-tokens" "$1"; }

token_claim() {
  python3 - "$1" "$2" <<'PY'
import base64, json, sys
payload = sys.argv[1].split(".")[1]
value = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
for part in sys.argv[2].split("."):
    value = value.get(part)
print(" ".join(value) if isinstance(value, list) else value)
PY
}

sign_in_default_staff() {
  sign_in_staff "$(staff_setting "$1_USERNAME")" "$(staff_setting "$1_PASSWORD")" ||
    fail "sign in of the default $1 account was refused: $(cat "$work/staff-refusal")"
  signed_in=$(staff_token access_token)
  [ "$(token_claim "$signed_in" realm_access.roles)" = "$(tr '[:upper:]' '[:lower:]' <<<"$1")" ] ||
    fail "the default $1 account carries the realm roles $(token_claim "$signed_in" realm_access.roles)"
  [ "$(token_claim "$signed_in" iss)" = "$admin/realms/staff" ] ||
    fail "the token of the default $1 account was issued by $(token_claim "$signed_in" iss)"
}

as_staff() {
  local role=$1
  shift
  sign_in_default_staff "$role"
  as "$signed_in" "$@"
}

refresh_status() {
  curl -sS -o "$work/body" -w '%{http_code}' --max-time 30 -d grant_type=refresh_token -d client_id=jaypay-admin-console \
    --data-urlencode "refresh_token=$1" "$admin/realms/staff/protocol/openid-connect/token" 2>"$work/curl-error" || true
}

keycloak_admin_sign_in() {
  curl -sS --fail-with-body --max-time 30 -d grant_type=password -d client_id=admin-cli -d username=admin \
    --data-urlencode "password=$(sed -n 's/^KEYCLOAK_ADMIN_PASSWORD=//p' "$env_file")" \
    "$keycloak/realms/master/protocol/openid-connect/token" >"$work/body" 2>"$work/curl-error" ||
    fail "the Keycloak admin sign in on $keycloak failed: $(response)"
  json_field access_token >"$work/keycloak-admin"
}

as_keycloak_admin() { as "$(cat "$work/keycloak-admin")" "$@"; }

create_staff() {
  local username=$1 password=$2 role=$3 id
  keycloak_admin_sign_in
  as_keycloak_admin expect "creation of the Keycloak user $username" 201 POST "$keycloak/admin/realms/staff/users" "$(hex 16)" \
    '{"username":"'$username'","enabled":true,"firstName":"Smoke","lastName":"Test","email":"'$username'@staff.jaypay.localhost","emailVerified":true}'
  as_keycloak_admin request "lookup of the Keycloak user $username" GET "$keycloak/admin/realms/staff/users?exact=true&username=$username" "$(hex 16)"
  id=$(json_field 0.id)
  [ -n "$id" ] || fail "the Keycloak user $username was not found: $(response)"
  smoke_users+=("$id")
  as_keycloak_admin expect "a weak password for $username" 400 PUT "$keycloak/admin/realms/staff/users/$id/reset-password" "$(hex 16)" \
    '{"type":"password","value":"weak","temporary":false}'
  as_keycloak_admin expect "the password of $username" 204 PUT "$keycloak/admin/realms/staff/users/$id/reset-password" "$(hex 16)" \
    '{"type":"password","value":"'$password'","temporary":false}'
  as_keycloak_admin request "the realm role $role" GET "$keycloak/admin/realms/staff/roles/$role" "$(hex 16)"
  as_keycloak_admin expect "the role $role for $username" 204 POST "$keycloak/admin/realms/staff/users/$id/role-mappings/realm" "$(hex 16)" \
    "[$(cat "$work/body")]"
  created_staff=$id
}

log "the customer site forwards only the requests its screens make"
for refused in \
  "POST $web/api/payment/payment/finish-settlement" \
  "POST $web/api/banking/banking/firmbanking/request" \
  "POST $web/api/money/money/increase-eda" \
  "GET $web/api/money/actuator/prometheus" \
  "GET $web/api/membership/actuator/health" \
  "GET $web/api/money/v3/api-docs" \
  "GET $web/api/payment/swagger-ui/index.html" \
  "PUT $web/api/membership/memberships/$membership_id" \
  "GET $web/api/membership/memberships/$membership_id/..%2F..%2Factuator/health" \
  "GET $web/api/money/money/..%2Factuator/prometheus" \
  "GET $web/api/membership/memberships/$membership_id;jsessionid=x"; do
  read -r method url <<<"$refused"
  expect "$method ${url#"$web"} through the customer site" 404 "$method" "$url" "$(hex 16)" '{}'
done

log "staff sign in through Keycloak with the authorization code flow and PKCE"
sign_in_default_staff ADMIN
sign_in_default_staff OPERATOR
sign_in_default_staff VIEWER
viewer_id_token=$(staff_token id_token)
viewer_refresh=$(staff_token refresh_token)

log "the admin console no longer signs staff in itself and keeps the master realm away"
expect "the old staff sign-in" 404 POST "$admin/api/auth/staff/sign-in" "$(hex 16)" '{}'
expect "the master realm through the admin console" 404 GET "$admin/realms/master/.well-known/openid-configuration" "$(hex 16)"

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
as_staff VIEWER request "admin member lookup by a viewer" GET "$member_url" "$gate_trace"
[ "$(json_field membershipId)" = "$membership_id" ] || fail "admin member lookup returned $(response)"
! json_has refreshToken || fail "admin member lookup answered with a refresh token: $(response)"
member=$(cat "$work/body")
as_staff VIEWER request "settlement list for a viewer" GET "$admin/api/payment/payment/normal-status" "$(hex 16)"
as_staff VIEWER expect "member edit by a viewer" 403 PUT "$member_url" "$(hex 16)" "$member"
[ "$(json_field error)" = FORBIDDEN ] || fail "member edit by a viewer answered $(response)"
for role in VIEWER OPERATOR; do
  as_staff "$role" expect "settling a payment by hand as $role" 403 POST "$admin/api/payment/payment/finish-settlement" "$(hex 16)" \
    '{"paymentId":"'$payment_id'"}'
done

log "a viewer follows each saga top-up step by step in the event flow"
flows="$admin/api/event-flow/flows"
expect "the event flows without a token" 401 GET "$flows" "$(hex 16)"
[ "$(json_field error)" = UNAUTHENTICATED ] || fail "the event flows without a token answered $(response)"
as "$customer_token" expect "the event flows with a customer token" 401 GET "$flows" "$(hex 16)"
as_staff VIEWER eventually 60 "the saga top-up smoke-saga-$run never showed every step as SUCCEEDED" flow_is RECHARGE "smoke-saga-$run" SUCCEEDED "" \
  "RECHARGE_REQUESTED MEMBERSHIP_CHECKED BANK_ACCOUNT_CHECKED BANK_CHARGED WALLET_INCREASED"
[ "$(json_field membershipId)" = "$membership_id" ] && [ "$(json_field amount)" = 10000 ] || fail "the flow smoke-saga-$run names $(response)"
as_staff VIEWER eventually 60 "the saga top-up of the unknown member never failed at the member check" flow_is RECHARGE "smoke-unknown-$run" FAILED MEMBERSHIP_CHECKED \
  "RECHARGE_REQUESTED MEMBERSHIP_CHECKED RECHARGE_FAILED"
as_staff VIEWER eventually 60 "the saga top-up the bank refused never failed at the charge" flow_is RECHARGE "smoke-refused-$run" FAILED BANK_CHARGED \
  "RECHARGE_REQUESTED MEMBERSHIP_CHECKED BANK_ACCOUNT_CHECKED BANK_CHARGED RECHARGE_FAILED"
! grep -q -e "5002-$run" -e "smoke-refused-$run@" -e "Smoke Refused" "$work/body" || fail "the flow smoke-refused-$run shows personal data: $(response)"
as_staff VIEWER request "the failed event flows" GET "$flows?status=FAILED" "$(hex 16)"
python3 -c 'import json, sys; ids = [f["flowId"] for f in json.load(open(sys.argv[1]))]; sys.exit(0 if {sys.argv[2], sys.argv[3]} <= set(ids) and sys.argv[4] not in ids else 1)' \
  "$work/body" "smoke-unknown-$run" "smoke-refused-$run" "smoke-saga-$run" || fail "the failed event flows were $(response)"
as_staff VIEWER expect "an event flow nobody started" 404 GET "$flows/RECHARGE/smoke-nothing-$run" "$(hex 16)"

log "a viewer follows each remittance and payment step by step in the event flow"
as_staff VIEWER eventually 60 "the remittance smoke-remit-$run never showed every step as SUCCEEDED" flow_is REMITTANCE "smoke-remit-$run" SUCCEEDED "" \
  "REMITTANCE_REQUESTED SENDER_DEBITED RECEIVER_CREDITED"
[ "$(json_field membershipId)" = "$membership_id" ] && [ "$(json_field amount)" = 5000 ] || fail "the flow smoke-remit-$run names $(response)"
as_staff VIEWER eventually 60 "the remittance above the balance never failed at the sender's debit" flow_is REMITTANCE "smoke-overdraw-remit-$run" FAILED SENDER_DEBITED \
  "REMITTANCE_REQUESTED SENDER_DEBITED"
as_staff VIEWER eventually 60 "the bank remittance smoke-bank-remit-$run never showed the bank transfer" flow_is REMITTANCE "smoke-bank-remit-$run" SUCCEEDED "" \
  "REMITTANCE_REQUESTED SENDER_DEBITED BANK_TRANSFERRED"
! grep -q -e "4002-$run" -e "Jay Bank" "$work/body" || fail "the flow smoke-bank-remit-$run shows the bank account: $(response)"
as_staff VIEWER eventually 60 "the payment smoke-pay-$run never showed every step as SUCCEEDED" flow_is PAYMENT "smoke-pay-$run" SUCCEEDED "" \
  "PAYMENT_REQUESTED PAYER_DEBITED"
[ "$(json_field membershipId)" = "$membership_id" ] && [ "$(json_field amount)" = 3000 ] || fail "the flow smoke-pay-$run names $(response)"
as_staff VIEWER eventually 60 "the payment above the balance never failed at the payer's debit" flow_is PAYMENT "smoke-overpay-$run" FAILED PAYER_DEBITED \
  "PAYMENT_REQUESTED PAYER_DEBITED"
as_staff VIEWER eventually 60 "the payment to a personal member never failed at the request" flow_is PAYMENT "smoke-personal-$run" FAILED PAYMENT_REQUESTED \
  "PAYMENT_REQUESTED"
as_staff VIEWER request "the payment event flows" GET "$flows?flowType=PAYMENT" "$(hex 16)"
python3 -c 'import json, sys; flows = json.load(open(sys.argv[1])); ids = [f["flowId"] for f in flows]; sys.exit(0 if {sys.argv[2], sys.argv[3]} <= set(ids) and all(f["flowType"] == "PAYMENT" for f in flows) else 1)' \
  "$work/body" "smoke-pay-$run" "smoke-overpay-$run" || fail "the payment event flows were $(response)"
as_staff VIEWER expect "the event flows of a type nobody knows" 400 GET "$flows?flowType=TRANSFER" "$(hex 16)"
[ "$(json_field error)" = INVALID_FLOW_TYPE ] || fail "the event flows of an unknown type answered $(response)"

log "the settlement job settles the payment once the bank confirms its payout"
as_staff VIEWER eventually 90 "the settlement job never settled payment $payment_id" payment_settled "$payment_id"
payout='{"requestId":"settlement-'$payment_id'","toBankName":"Jay Bank","toBankAccountNumber":"3002-'$run'","moneyAmount":2910}'
request "the payout the settlement job sent, sent again" POST "$banking/banking/firmbanking/request" "$(hex 16)" "$payout"
[ "$(json_field transferStatus)" = SUCCEEDED ] || fail "the bank holds no settled payout for payment $payment_id: $(response)"
[ "$(json_field fromBankAccountNumber)" = "$company_account" ] || fail "the payout of payment $payment_id left another account than the bank transfer: $(response)"
expect "the payout id of payment $payment_id for another amount" 409 POST "$banking/banking/firmbanking/request" "$(hex 16)" \
  '{"requestId":"settlement-'$payment_id'","toBankName":"Jay Bank","toBankAccountNumber":"3002-'$run'","moneyAmount":2911}'
as_staff VIEWER eventually 60 "the settlement of payment $payment_id never showed every step as SUCCEEDED" flow_is SETTLEMENT "settlement-$payment_id" SUCCEEDED "" \
  "SETTLEMENT_STARTED MERCHANT_PAID_OUT PAYMENT_SETTLED"
[ "$(json_field membershipId)" = "$merchant" ] && [ "$(json_field amount)" = 2910 ] || fail "the flow settlement-$payment_id names $(response)"
! grep -q -e "3002-$run" -e "$company_account" "$work/body" || fail "the flow settlement-$payment_id shows a bank account: $(response)"

log "a viewer reads the saga top-ups of region $region, and the total survives a new DynamoDB container"
as_staff VIEWER eventually 60 "the region total never reached 19000" region_total_is 19000
recreate_dynamodb
as_staff VIEWER eventually 30 "the region total did not survive a new DynamoDB container" region_total_is 19000

log "an operator edits a member"
as_staff OPERATOR request "member edit by an operator" PUT "$member_url" "$(hex 16)" "$member"

log "signing out ends the Keycloak session and its refresh token"
call GET "$admin/realms/staff/protocol/openid-connect/logout?client_id=jaypay-admin-console&id_token_hint=$viewer_id_token&post_logout_redirect_uri=$admin%2Fsigned-out" "$(hex 16)"
[ "$http_status" = 302 ] || fail "sign out returned HTTP $http_status: $(response)"
[ "$(refresh_status "$viewer_refresh")" = 400 ] || fail "the refresh token still worked after sign out: $(response)"

log "no service port, and neither site, serves Actuator or the OpenAPI pages, and Prometheus scrapes the management port"
for service_url in "${service_urls[@]}"; do
  call GET "$service_url/no-such-path" "$(hex 16)"
  unmapped=$http_status
  case "$unmapped" in
    401 | 404) ;;
    *) fail "an unknown path on $service_url returned HTTP $unmapped: $(response)" ;;
  esac
  for path in /actuator/health /actuator/prometheus /v3/api-docs /swagger-ui/index.html; do
    expect "$path on $service_url" "$unmapped" GET "$service_url$path" "$(hex 16)"
  done
done
expect "the actuator of a service behind the console without a token" 401 GET "$admin/api/membership/actuator/health" "$(hex 16)"
as_staff ADMIN expect "the actuator of a service behind the console" 403 GET "$admin/api/membership/actuator/health" "$(hex 16)"
as_staff ADMIN expect "the API docs of a service behind the console" 403 GET "$admin/api/payment/v3/api-docs" "$(hex 16)"
expect "the actuator of the auth service through the console" 404 GET "$admin/api/auth/actuator/health" "$(hex 16)"
eventually 60 "Prometheus does not scrape every service on port $management_port" targets_up
log "$(cat "$work/last")"

log "PostgreSQL keeps at least a fifth of its memory limit free"
postgres_memory_percent=$(postgres_memory_percent) || fail "could not read the memory of the postgres container"
[ "$postgres_memory_percent" -le 80 ] || fail "PostgreSQL uses $postgres_memory_percent% of its memory limit"
log "PostgreSQL uses $postgres_memory_percent% of its memory limit"

log "a staff account made in Keycloak signs in, and once disabled it can no longer sign in or refresh"
new_staff="smoke-$run"
new_password="Smoke-$(hex 8)-Pass-42!"
create_staff "$new_staff" "$new_password" operator
new_id=$created_staff
sign_in_staff "$new_staff" "$new_password" || fail "sign in of the new staff account was refused: $(cat "$work/staff-refusal")"
new_token=$(staff_token access_token)
new_refresh=$(staff_token refresh_token)
as "$new_token" request "member lookup by the new staff account" GET "$member_url" "$(hex 16)"
as_keycloak_admin expect "disabling the new staff account" 204 PUT "$keycloak/admin/realms/staff/users/$new_id" "$(hex 16)" '{"enabled":false}'
staff_sign_in_refused "$new_staff" "$new_password" "the disabled staff account was not refused"
[ "$(refresh_status "$new_refresh")" = 400 ] || fail "the disabled staff account refreshed its token: $(response)"
as "$new_token" call GET "$member_url" "$(hex 16)"
log "the access token of the disabled account still answers HTTP $http_status and expires in $(($(token_claim "$new_token" exp) - $(date +%s)))s"

log "five wrong passwords lock a staff account, and then even the right password is refused"
locked_staff="smoke-lock-$run"
locked_password="Smoke-$(hex 8)-Lock-42!"
create_staff "$locked_staff" "$locked_password" viewer
locked_id=$created_staff
for attempt in 1 2 3 4 5; do
  sleep 2
  staff_sign_in_refused "$locked_staff" "Wrong-$(hex 8)-Pass-$attempt!" "a wrong password was not refused"
done
staff_sign_in_refused "$locked_staff" "$locked_password" "the right password was not refused on a locked account"
keycloak_admin_sign_in
as_keycloak_admin request "brute force status of $locked_staff" GET "$keycloak/admin/realms/staff/attack-detection/brute-force/users/$locked_id" "$(hex 16)"
[ "$(json_field disabled)" = True ] || fail "Keycloak does not report $locked_staff as locked: $(response)"
[ "$(json_field numFailures)" -ge 5 ] || fail "Keycloak counted fewer than five failures: $(response)"

log "the gate refuses what the admin console never asks for"
as_staff ADMIN expect "payment request through the admin console" 403 POST "$admin/api/payment/payment/request" "$(hex 16)" \
  '{"requestId":"smoke-admin-'$run'","requestMembershipId":"'$membership_id'","franchiseId":"'$merchant'","requestPrice":1}'
as_staff ADMIN expect "a path that climbs out of its segment" 403 GET "$member_url/..%2F..%2Fregister" "$(hex 16)"

log "a staff token means nothing to the customer site"
sign_in_default_staff ADMIN
expect "token validation of a staff token on the web site" 200 POST "$web/api/membership/memberships/token-validate" "$(hex 16)" '{"jwtToken":"'$signed_in'"}'
[ "$(cat "$work/body")" = false ] || fail "the web site did not reject a staff token: $(response)"

log "every service shipped its logs to ClickHouse"
for service in "${services[@]}"; do
  eventually 60 "no logs from $service in ClickHouse" service_logged_since_start "$service"
done

log "traces cross the service boundaries and lead to their logs"
for check in \
  "HTTP:$payment_trace:payment-service,membership-service,money-service" \
  "Gate:$gate_trace:auth-service,membership-service" \
  "Kafka:$kafka_trace:money-service,logging-consumer" \
  "Axon:$saga_trace:money-service,banking-service"; do
  IFS=: read -r boundary trace required <<<"$check"
  eventually 60 "the $boundary trace does not span $required" trace_spans "$trace" "$required"
  eventually 30 "no logs carry the $boundary trace id" trace_has_logs "$trace"
  log "$boundary trace $trace spans $required"
done

log "anonymous Grafana visitors read dashboards but cannot change anything"
request "anonymous dashboard search" GET "$grafana/api/search" "$(hex 16)"
for write in \
  'api/dashboards/db {"dashboard":{"title":"smoke-'$run'","panels":[]},"overwrite":false}' \
  'api/folders {"title":"smoke-'$run'"}' \
  'api/datasources {"name":"smoke-'$run'","type":"prometheus","access":"proxy","url":"http://localhost"}' \
  'api/admin/users {"login":"smoke-'$run'","password":"Smoke-'$(hex 8)'"}'; do
  read -r path body <<<"$write"
  call POST "$grafana/$path" "$(hex 16)" "$body"
  case "$http_status" in
    401 | 403) ;;
    *) fail "anonymous POST /$path returned HTTP $http_status: $(response)" ;;
  esac
done

log "the ClickHouse user behind Grafana reads the telemetry but cannot change it"
[ "$(as_grafana_reader "SELECT count() > 0 FROM otel_logs")" = 200 ] && [ "$(cat "$work/body")" = 1 ] ||
  fail "the grafana user could not read otel_logs: $(response)"
for statement in "CREATE TABLE otel.smoke_$run (id UInt8) ENGINE = Memory" "DROP TABLE otel.otel_logs" \
  "INSERT INTO otel.otel_logs (ServiceName) VALUES ('smoke')" "SELECT count() FROM system.users"; do
  [ "$(as_grafana_reader "$statement")" = 403 ] && grep -q '(ACCESS_DENIED)' "$work/body" || fail "the grafana user was not refused $statement: $(response)"
done

log "the Vault token of membership-service reads its key and nothing else"
[ "$(as_membership_vault GET kv-v1/data/encrypt/data/dbkey)" = 200 ] || fail "membership-service could not read its key: $(response)"
[ "$(as_membership_vault POST kv-v1/data/encrypt/data/dbkey '{"data":{"key":"smoke"}}')" = 403 ] || fail "membership-service could overwrite its key: $(response)"
[ "$(as_membership_vault GET sys/mounts)" = 403 ] || fail "membership-service could list the Vault mounts: $(response)"
[ "$(as_membership_vault POST auth/token/create '{}')" = 403 ] || fail "membership-service could create a Vault token: $(response)"

if [ "$stack" != kubernetes ]; then
  log "Vault holds the key again after it restarts"
  docker restart "$(docker compose -f "$compose_file" ps -q vault)" >/dev/null || fail "could not restart the vault container"
  restored=no
  for _ in $(seq 1 30); do
    [ "$(as_membership_vault GET kv-v1/data/encrypt/data/dbkey)" = 200 ] && restored=yes && break
    sleep 2
  done
  [ "$restored" = yes ] || fail "membership-service could not read its key after Vault restarted: $(response)"
fi

log "no container runs as root and every port stays on the loopback address"
running_as_root=$(root_containers)
[ -z "$running_as_root" ] || fail "these containers run as root: $(echo $running_as_root)"
wide=$(wide_ports)
[ -z "$wide" ] || fail "these ports are reachable beyond the loopback address: $(echo $wide)"

log "PASSED"
