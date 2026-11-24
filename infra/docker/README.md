# Local stack

`compose.yaml` runs every JayPay service, both web front ends, the infrastructure they need and the observability stack as one Compose project named `jaypay`. Every host port binds to `127.0.0.1`.

## Run it

From the repository root:

```sh
python3 scripts/init-env.py
make images
docker compose -f infra/docker/compose.yaml up -d --wait
scripts/smoke.sh
```

1. `scripts/init-env.py` writes `infra/docker/.env` from `.env.example`. It generates every `<hex:N>` secret, keeps values that are already set, and never prints them. Run it again after `.env.example` gains a key.
2. `make images` builds the service images and both front-end images. Compose builds the OpenTelemetry Collector image on first start.
3. `up -d --wait` returns once every container reports healthy.
4. `scripts/smoke.sh` signs a member up through the web site, registers a bank account, tops the wallet up through the Axon saga and through the Kafka task, pays, and then checks that every service shipped logs to ClickHouse, that the HTTP, Kafka and Axon traces cross services and lead to their logs, and that Prometheus scrapes every service. It exits non-zero and prints the cause on the first failure.

Stop the stack with `docker compose -f infra/docker/compose.yaml down`. Add `-v` to drop the named volumes as well.

## Addresses

| What | Address |
| --- | --- |
| Web site | http://localhost:8090 |
| Admin console | http://localhost:8091 |
| Grafana, no login | http://localhost:3030 |
| Prometheus | http://localhost:9090 |
| Kafka UI | http://localhost:8989 |
| Axon Server | http://localhost:8024, gRPC on 8124 |
| ClickHouse HTTP, user `otel` | http://localhost:8123 |
| Vault, dev mode | http://localhost:8200 |
| DynamoDB Local | http://localhost:8000 |
| PostgreSQL, user `jaypay` | localhost:5432 |
| Kafka | localhost:9092 |
| OTLP gRPC and HTTP | localhost:4317, localhost:4318 |
| Services | membership 8081, banking 8082, money 8083, remittance 8084, payment 8085, money-aggregation 8086, money-query 8087, settlement 8088 |

The front ends use 8090 and 8091 because 3000 and 3001 are common development server ports. The front ends proxy `/api/<service>/...` to the services by their Compose names, so they need no environment.

## Observability

| Signal | Path | Store |
| --- | --- | --- |
| Logs | Logback, OpenTelemetry appender, OTLP HTTP, Collector | ClickHouse `otel.otel_logs`, 72 hours |
| Traces | Micrometer Tracing across HTTP, Kafka and Axon, OTLP HTTP, Collector | ClickHouse `otel.otel_traces`, 72 hours |
| Metrics | Micrometer, `/actuator/prometheus` | Prometheus, 3 days |

Each log record carries the trace and span id of the request that wrote it. The Collector drops the spans of `/actuator` requests. Container output is ECS JSON because `LOG_FORMAT=ecs`; leave the variable unset to get plain text.

Grafana provisions both data sources and three dashboards in the JayPay folder: the service overview (availability, HTTP, Kafka, JVM and connection pools), logs (filter by service, text or trace id) and traces (recent traces, the waterfall of one trace and its logs).

## Environment

`.env` holds secrets only. Everything else is fixed in `compose.yaml`, and a service receives a variable only when its configuration reads it and the value differs from its default.

| Key | Purpose | Required | Example |
| --- | --- | --- | --- |
| `POSTGRES_PASSWORD` | Password of the PostgreSQL user `jaypay`, used by every service that has a database | yes | 48 hex characters |
| `VAULT_TOKEN` | Root token of the Vault dev server, used by membership-service to read its encryption key | yes | 48 hex characters |
| `ENCRYPTION_KEY` | AES key written to Vault at `kv-v1/data/encrypt/dbkey` when Vault starts; membership-service encrypts member data with it | yes | 32 hex characters |
| `ENCRYPTION_FALLBACK_KEY` | AES key membership-service uses when Vault has no key | yes | 32 hex characters |
| `JWT_SECRET` | Signing key for the tokens membership-service issues | yes | 128 hex characters |
| `CLICKHOUSE_PASSWORD` | Password of the ClickHouse user `otel`, used by the Collector and Grafana | yes | 48 hex characters |
| `GRAFANA_ADMIN_PASSWORD` | Password of the Grafana `admin` account for the HTTP API; the web UI needs no login | yes | 48 hex characters |

`python3 scripts/check-env.py .` checks that `compose.yaml` and `.env.example` name the same keys, and that every variable a service receives is one the service reads without repeating its default. `make verify` runs it as the `env` step.
