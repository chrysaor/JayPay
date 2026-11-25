#!/bin/sh
set -eu

until pg_isready -q -h 127.0.0.1 -U "$POSTGRES_USER"; do
  sleep 1
done

for database in $(sed -n 's/^CREATE DATABASE \([a-z_]*\);$/\1/p' /docker-entrypoint-initdb.d/01-databases.sql); do
  found=$(psql -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$database'")
  if [ -z "$found" ]; then
    psql -h 127.0.0.1 -U "$POSTGRES_USER" -d postgres -qc "CREATE DATABASE $database"
  fi
done
