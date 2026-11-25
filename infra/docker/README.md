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

1. `scripts/init-env.py` writes `infra/docker/.env` from `.env.example`. It replaces `<hex:N>` with N random bytes in hex and `<password:N>` with N random characters that pass the staff password rules, keeps a lowercase prefix such as `admin-` in front of either, keeps values that are already set, and never prints them. Run it again after `.env.example` gains a key.
2. `make images` builds the service images and both front-end images. Compose builds the OpenTelemetry Collector image on first start.
3. `up -d --wait` returns once every container reports healthy.
4. `scripts/smoke.sh` signs a member up through the web site, registers a bank account, tops the wallet up through the Axon saga and through the Kafka task, pays, signs the three default staff accounts in to the admin console, checks that a request without a staff token or with a customer token is refused, that a viewer cannot write, that the saga top-ups of the run's own region add up in DynamoDB and are still there after it removes and recreates the `dynamodb` container, that an operator cannot manage staff accounts, and that an account an admin creates and deactivates can no longer sign in, and then checks that every service shipped logs to ClickHouse, that the HTTP, Kafka and Axon traces cross services and lead to their logs, and that Prometheus scrapes every service. It exits non-zero and prints the cause on the first failure.

Stop the stack with `docker compose -f infra/docker/compose.yaml down`. Add `-v` to drop the named volumes as well.

`postgres/initdb.d` creates one database per service only when the `jaypay_postgres-data` volume is new. A volume from before auth-service has no `auth` database, so auth-service and the PostgreSQL healthcheck fail. Run `docker volume rm jaypay_postgres-data` after `make down` to start over.

DynamoDB Local keeps its data in the `jaypay_dynamodb-data` volume, so the region totals of money-query-service survive `make down`. The container runs as root because the image's `dynamodblocal` user cannot write to a new named volume. When the container starts, `dynamodb/table.sh` creates the table `MoneyIncreaseEventByRegion` from `infra/terraform/modules/dynamodb-money-sum/table.json` unless it exists, the same file Terraform applies to AWS, see [infra/terraform/README.md](../terraform/README.md). Run `docker volume rm jaypay_dynamodb-data` after `make down` to start over. DynamoDB Local accepts an access key of letters and digits only, which is why money-query-service gets `dynamodblocal`.

`compose.aws.yaml` points money-query-service at a real DynamoDB table instead. [infra/terraform/README.md](../terraform/README.md) has the steps.

membership-service encrypts member emails with AES-GCM and cannot read rows written by its earlier ECB-mode code, so looking those members up fails. If your `jaypay_postgres-data` volume holds such rows, run `docker volume rm jaypay_postgres-data` after `make down`.

## Addresses

| What | Address |
| --- | --- |
| Web site | http://localhost:8090 |
| Admin console, staff sign-in | http://localhost:8091 |
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
| Services | membership 8081, banking 8082, money 8083, remittance 8084, payment 8085, money-aggregation 8086, money-query 8087, settlement 8088, auth 8089 |

The front ends use 8090 and 8091 because 3000 and 3001 are common development server ports. The front ends proxy `/api/<service>/...` to the services by their Compose names, so they need no environment. The admin console sends every `/api/<service>/` request to auth-service first through nginx `auth_request` and forwards it only on a 204, and it proxies `/api/auth/staff/` to auth-service for sign-in and account management.

## Staff accounts

auth-service creates one staff account per role the first time it starts with an empty `auth` database, from the `STAFF_*` keys below, and leaves an account that already exists as it is. Sign in to http://localhost:8091 with those values. A role includes everything the role below it can do.

| Role | Can |
| --- | --- |
| viewer | Read every screen of the admin console and change its own password |
| operator | Also edit members and mark payments settled |
| admin | Also create staff accounts, change their roles, turn their MFA flag on and off, and deactivate them; an admin cannot change its own role or deactivate itself |

A password needs at least 12 characters and at most 72 bytes, an uppercase letter, a lowercase letter, a digit and a symbol, must not contain the username, and must not be on the built-in list of common passwords. `PasswordPolicy` in auth-service holds the rules, and the API and the screen name each rule a password breaks. A staff token lasts 8 hours, and a role change or deactivation applies to it on the next request.

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
| `STAFF_JWT_SECRET` | Signing key for the staff tokens auth-service issues; separate from `JWT_SECRET`, so a staff token and a customer token never open each other's site | yes | 64 hex characters |
| `STAFF_ADMIN_USERNAME`, `STAFF_ADMIN_PASSWORD` | The admin account auth-service creates when it is missing | yes | `admin-` and 6 hex characters, 24 random characters |
| `STAFF_OPERATOR_USERNAME`, `STAFF_OPERATOR_PASSWORD` | The operator account auth-service creates when it is missing | yes | `operator-` and 6 hex characters, 24 random characters |
| `STAFF_VIEWER_USERNAME`, `STAFF_VIEWER_PASSWORD` | The viewer account auth-service creates when it is missing | yes | `viewer-` and 6 hex characters, 24 random characters |

`python3 scripts/check-env.py .` checks that `compose.yaml` and `.env.example` name the same keys, and that every variable a service receives is one the service reads without repeating its default. `make verify` runs it as the `env` step.
