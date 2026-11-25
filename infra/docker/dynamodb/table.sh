#!/usr/bin/env bash
set -euo pipefail

endpoint=http://127.0.0.1:8000
table=MoneyIncreaseEventByRegion

call() {
  curl -fsS --max-time 5 "$endpoint" \
    -H "Content-Type: application/x-amz-json-1.0" \
    -H "X-Amz-Target: DynamoDB_20120810.$1" \
    -H "Authorization: AWS4-HMAC-SHA256 Credential=local/20260101/ap-northeast-2/dynamodb/aws4_request, SignedHeaders=host, Signature=local" \
    -d "$2"
}

describe() {
  call DescribeTable "{\"TableName\":\"$table\"}" >/dev/null 2>&1
}

create() {
  for _ in $(seq 1 60); do
    curl -s -o /dev/null --max-time 2 "$endpoint" && break
    sleep 1
  done
  describe && return
  call CreateTable "{
    \"TableName\": \"$table\",
    \"AttributeDefinitions\": [
      {\"AttributeName\": \"PK\", \"AttributeType\": \"S\"},
      {\"AttributeName\": \"SK\", \"AttributeType\": \"S\"}
    ],
    \"KeySchema\": [
      {\"AttributeName\": \"PK\", \"KeyType\": \"HASH\"},
      {\"AttributeName\": \"SK\", \"KeyType\": \"RANGE\"}
    ],
    \"BillingMode\": \"PAY_PER_REQUEST\"
  }" >/dev/null
}

case "${1:-}" in
  create) create ;;
  check) describe ;;
  *) echo "usage: $0 create|check" >&2; exit 2 ;;
esac
