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

1. `scripts/init-env.py` writes `infra/docker/.env` from `.env.example`. It replaces `<hex:N>` with N random bytes in hex and `<password:N>` with N random characters that pass the Keycloak password policy of the realm `staff`, keeps a lowercase prefix such as `admin-` in front of either, keeps values that are already set, and never prints them. Run it again after `.env.example` gains a key.
2. `make images` builds the service images and both front-end images. Compose builds the OpenTelemetry Collector image on first start.
3. `up -d --wait` returns once every container reports healthy.
4. `scripts/smoke.sh` signs a member up through the web site, registers a bank account, tops the wallet up through the Axon saga and through the Kafka task, pays, signs the three default staff accounts in to the admin console through the real Keycloak Authorization Code flow with PKCE, checks that a request without a staff token or with a customer token is refused, that a viewer cannot write, that the saga top-ups of the run's own region add up in DynamoDB and are still there after it removes and recreates the `dynamodb` container, that signing out ends the Keycloak session, that an account created through the Keycloak admin API is refused a weak password, signs in, and once disabled can neither sign in nor refresh, that five wrong passwords lock an account so that even the right password is refused, and then checks that every service shipped logs to ClickHouse, that the HTTP, Kafka and Axon traces cross services and lead to their logs, and that Prometheus scrapes every service. It exits non-zero and prints the cause on the first failure.

Stop the stack with `docker compose -f infra/docker/compose.yaml down`. Add `-v` to drop the named volumes as well.

`postgres/initdb.d/01-databases.sql` creates one database per service and the `keycloak` database when the `jaypay_postgres-data` volume is new. PostgreSQL runs that directory only on an empty volume, so `postgres/create-databases.sh` runs as the `post_start` hook of the container on every start, waits for the server and creates any database from `01-databases.sql` that the volume lacks. An existing volume therefore gains the `keycloak` database on the next `make up`. The PostgreSQL healthcheck connects to `keycloak`, so Keycloak starts only after that database exists. The `auth` database of earlier versions stays in an old volume unused.

DynamoDB Local keeps its data in the `jaypay_dynamodb-data` volume, so the region totals of money-query-service survive `make down`. The container runs as root because the image's `dynamodblocal` user cannot write to a new named volume. When the container starts, `dynamodb/table.sh` creates the table `MoneyIncreaseEventByRegion` from `infra/terraform/modules/dynamodb-money-sum/table.json` unless it exists, the same file Terraform applies to AWS, see [infra/terraform/README.md](../terraform/README.md). Run `docker volume rm jaypay_dynamodb-data` after `make down` to start over. DynamoDB Local accepts an access key of letters and digits only, which is why money-query-service gets `dynamodblocal`.

`compose.aws.yaml` points money-query-service at a real DynamoDB table instead. [infra/terraform/README.md](../terraform/README.md) has the steps.

membership-service encrypts member emails with AES-GCM and cannot read rows written by its earlier ECB-mode code, so looking those members up fails. If your `jaypay_postgres-data` volume holds such rows, run `docker volume rm jaypay_postgres-data` after `make down`.

## Addresses

| What | Address |
| --- | --- |
| Web site | http://localhost:8090 |
| Admin console, staff sign-in | http://localhost:8091 |
| Keycloak admin console, user `admin` with `KEYCLOAK_ADMIN_PASSWORD` | http://localhost:8092 |
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

The front ends use 8090 and 8091 because 3000 and 3001 are common development server ports. The front ends proxy `/api/<service>/...` to the services by their Compose names, so they need no environment. The admin console sends every `/api/<service>/` request to auth-service first through nginx `auth_request` and forwards it only on a 204, and it proxies `/realms/staff/` and `/resources/` to Keycloak for the sign-in pages.

## Staff accounts

Keycloak keeps the staff accounts in the realm `staff`. When Keycloak starts with an empty `keycloak` database, `--import-realm` reads `keycloak/import/staff.json`, which defines the realm, its roles, the client `jaypay-admin-console` and one account per role. The file holds no password: Keycloak replaces `${STAFF_ADMIN_USERNAME}`, `${STAFF_ADMIN_PASSWORD}` and the other `STAFF_*` placeholders with the environment of the container while it imports. The import skips a realm that exists, so a later change to the file does not reach an existing database. Apply such a change in the Keycloak admin console, or drop the `keycloak` database to import again.

Sign in to http://localhost:8091 with the `STAFF_*` values. Manage accounts, roles and passwords in the Keycloak admin console at http://localhost:8092 as `admin` with `KEYCLOAK_ADMIN_PASSWORD`, in the realm `staff`. It replaced the staff accounts page of the admin console. A role includes everything the role below it can do.

| Realm role | Can |
| --- | --- |
| viewer | Read every screen of the admin console |
| operator | Also edit members and mark payments settled |
| admin | Everything an operator can do |

The password policy asks for 12 to 128 characters, an uppercase letter, a lowercase letter, a digit and a symbol, not the username or email, not one of the last 5 passwords, and not a password from `keycloak/password-blacklists/common-passwords.txt`. Five wrong passwords lock an account for a minute, longer with each further failure up to 15 minutes, and two failures within a second lock it at once. Staff change their own password and add an authenticator app at http://localhost:8091/realms/staff/account. The app is optional. An access token lasts 2 minutes and the console refreshes it, and a session ends after 30 idle minutes or 8 hours.

Keycloak runs `start` with `KC_HTTP_ENABLED`, because the local stack has no TLS. `KC_HOSTNAME` is the admin site's URL, so every token carries the issuer `http://localhost:8091/realms/staff`, which auth-service expects. [docs/architecture.md](../../docs/architecture.md#one-origin-for-the-browser-and-the-containers) explains why.

## Observability

| Signal | Path | Store |
| --- | --- | --- |
| Logs | Logback, OpenTelemetry appender, OTLP HTTP, Collector | ClickHouse `otel.otel_logs`, 72 hours |
| Traces | Micrometer Tracing across HTTP, Kafka and Axon, OTLP HTTP, Collector | ClickHouse `otel.otel_traces`, 72 hours |
| Metrics | Micrometer, `/actuator/prometheus` on the management port 9080 | Prometheus, 3 days |

Actuator listens on the management port 9080 of each service container and exposes only health, with the liveness and readiness groups, and the Prometheus metrics. Compose publishes no host port for it: the image's `HEALTHCHECK` calls it from inside the container and Prometheus scrapes it over the Compose network. The service ports answer `/actuator/**`, `/v3/api-docs` and `/swagger-ui/**` like any unknown path. To read the OpenAPI document or Swagger UI, run a service from the checkout with `SPRING_PROFILES_ACTIVE=local`. Each log record carries the trace and span id of the request that wrote it. The Collector drops the spans of `/actuator` requests. Container output is ECS JSON because `LOG_FORMAT=ecs`; leave the variable unset to get plain text.

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
| `KEYCLOAK_ADMIN_PASSWORD` | Password of the Keycloak `admin` account in the master realm, which Keycloak creates on its first start | yes | 24 random characters |
| `STAFF_ADMIN_USERNAME`, `STAFF_ADMIN_PASSWORD` | The admin account the realm import creates in Keycloak | yes | `admin-` and 6 hex characters, 24 random characters |
| `STAFF_OPERATOR_USERNAME`, `STAFF_OPERATOR_PASSWORD` | The operator account the realm import creates in Keycloak | yes | `operator-` and 6 hex characters, 24 random characters |
| `STAFF_VIEWER_USERNAME`, `STAFF_VIEWER_PASSWORD` | The viewer account the realm import creates in Keycloak | yes | `viewer-` and 6 hex characters, 24 random characters |

`python3 scripts/check-env.py .` checks that `compose.yaml` and `.env.example` name the same keys, and that every variable a service receives is one the service reads without repeating its default. `make verify` runs it as the `env` step.
