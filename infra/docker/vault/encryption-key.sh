#!/bin/sh
set -eu

export VAULT_ADDR=http://127.0.0.1:8200
mount=kv-v1/data/encrypt

write() {
  for _ in $(seq 1 60); do
    vault status >/dev/null 2>&1 && break
    sleep 1
  done
  vault secrets list | grep -q "^$mount/ " || vault secrets enable -path="$mount" -version=2 kv >/dev/null
  vault kv put -mount="$mount" dbkey key="$ENCRYPTION_KEY" >/dev/null
}

check() {
  vault kv get -mount="$mount" -field=key dbkey >/dev/null
}

case "${1:-}" in
  write) write ;;
  check) check ;;
  *) echo "usage: $0 write|check" >&2; exit 2 ;;
esac
