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
4. `scripts/smoke.sh` signs a member up through the web site, registers a bank account, tops the wallet up twice through the Axon saga, checks that a saga top-up of a member that membership-service does not know ends as failed and moves no money, checks that a viewer sees each of those top-ups and one the bank refuses as an event flow with every step and the step that failed, pays, signs the three default staff accounts in to the admin console through the real Keycloak Authorization Code flow with PKCE, checks that a request without a staff token or with a customer token is refused, that a viewer cannot write, that the saga top-ups of the run's own region add up in DynamoDB and are still there after it removes and recreates the `dynamodb` container, that signing out ends the Keycloak session, that an account created through the Keycloak admin API is refused a weak password, signs in, and once disabled can neither sign in nor refresh, that five wrong passwords lock an account so that even the right password is refused, and then checks that every service shipped logs to ClickHouse, that the HTTP, Kafka and Axon traces cross services and lead to their logs, that Prometheus scrapes every service, that an anonymous Grafana visitor cannot create a dashboard, folder, datasource or user, that the ClickHouse user `grafana` reads `otel` but cannot create, drop or insert, that the Vault token of membership-service reads its key but cannot write it, list mounts or create tokens, and that no container runs as root or publishes a port beyond `127.0.0.1`. It exits non-zero and prints the cause on the first failure.

Stop the stack with `docker compose -f infra/docker/compose.yaml down`. Add `-v` to drop the named volumes as well.

`postgres/initdb.d/01-databases.sql` creates one database per service and the `keycloak` database when the `jaypay_postgres-data` volume is new. PostgreSQL runs that directory only on an empty volume, so `postgres/create-databases.sh` runs as the `post_start` hook of the container on every start, waits for the server and creates any database from `01-databases.sql` that the volume lacks. An existing volume therefore gains the `keycloak` database on the next `make up`. The PostgreSQL healthcheck connects to `keycloak`, so Keycloak starts only after that database exists. The `auth` database of earlier versions stays in an old volume unused.

DynamoDB Local keeps its data in the `jaypay_dynamodb-data` volume, so the region totals of money-query-service survive `make down`. The container runs as the image's `dynamodblocal` user, 1000. Before it starts, the one-shot container `dynamodb-data-owner` gives that user the volume, which Docker creates owned by root, with nothing but the `CHOWN` capability and no network, and exits. When the container starts, `dynamodb/table.sh` creates the table `MoneyIncreaseEventByRegion` from `infra/terraform/modules/dynamodb-money-sum/table.json` unless it exists, the same file Terraform applies to AWS, see [infra/terraform/README.md](../terraform/README.md). Run `docker volume rm jaypay_dynamodb-data` after `make down` to start over. DynamoDB Local accepts an access key of letters and digits only, which is why money-query-service gets `dynamodblocal`.

`compose.aws.yaml` points money-query-service at a real DynamoDB table instead. [infra/terraform/README.md](../terraform/README.md) has the steps.

## Container privileges

Every container runs as a numeric non-root user, drops every Linux capability, sets `no-new-privileges` and, except Keycloak, has a read-only root file system. A container writes only to its named volume and to the `tmpfs` mounts that `compose.yaml` lists for it, which the local cluster mirrors as `emptyDir` volumes. `make verify` fails when a published port binds to another address than `127.0.0.1`, when a container lacks one of these settings, or when the cluster runs a container with another user, root file system or set of scratch paths than Compose.

| Container | User | Read-only root | Written outside its volume |
| --- | --- | --- | --- |
| Services | 10001 | yes | `/tmp` |
| Web site, admin console | 101 (nginx) | yes | `/tmp`, `/etc/nginx/conf.d` for the `default.conf` the image renders from its template |
| PostgreSQL | 70 (postgres) | yes | `/var/run/postgresql`, `/tmp` |
| Keycloak | 1000 (keycloak) | no | Keycloak rebuilds its Quarkus application under `/opt/keycloak/lib` on every `start` that is not `--optimized`, and running optimized needs an image built for it |
| Kafka | 1000 (appuser) | yes | `/opt/kafka/config` and `/opt/kafka/logs`, which the entrypoint writes from the environment, `/etc/kafka/secrets`, `/mnt/shared/config`, `/tmp` |
| Kafka UI | 100 | yes | `/tmp` |
| Axon Server | 1001 | yes | `/axonserver/exts`, `/axonserver/plugins`, `/axonserver/transformation`, `/tmp` |
| Vault | 100 (vault) | yes | `/vault/file`, `/vault/logs`, `/home/vault` for the token the CLI keeps |
| DynamoDB Local | 1000 (dynamodblocal) | yes | `/tmp`, mounted executable because sqlite4java unpacks its native library there |
| ClickHouse | 101 (clickhouse) | yes | `/tmp`. The users come from `clickhouse/users.d/jaypay.xml`, so `CLICKHOUSE_SKIP_USER_SETUP` keeps the entrypoint from writing one |
| OpenTelemetry Collector | 10001 | yes | nothing |
| Prometheus | 65534 (nobody) | yes | nothing |
| Grafana | 472 (grafana) | yes | `/tmp` |

Vault dev mode cannot be hardened further: it keeps everything in memory, starts unsealed with the root token from `VAULT_TOKEN` and listens on plain HTTP. `vault/encryption-key.sh` writes `ENCRYPTION_KEY` and creates `VAULT_MEMBERSHIP_TOKEN` with a policy that reads only `kv-v1/data/encrypt/dbkey`, so membership-service never holds the root token. The ClickHouse user `otel` keeps full rights because the Collector creates its tables; the user `grafana` runs with `readonly = 2` and `SELECT` on `otel.*` only. Anonymous Grafana visitors are viewers, and Kafka UI runs with `KAFKA_CLUSTERS_0_READONLY`. Kafka, Axon Server and DynamoDB Local have no authentication on their loopback ports.

membership-service encrypts member emails with AES-GCM and cannot read rows written by its earlier ECB-mode code, so looking those members up fails. If your `jaypay_postgres-data` volume holds such rows, run `docker volume rm jaypay_postgres-data` after `make down`.

## Addresses

| What | Address |
| --- | --- |
| Web site | http://localhost:8090 |
| Admin console, staff sign-in | http://localhost:8091 |
| Keycloak admin console, user `admin` with `KEYCLOAK_ADMIN_PASSWORD` | http://localhost:8092 |
| Grafana, view without a login | http://localhost:3030 |
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
| `VAULT_TOKEN` | Root token of the Vault dev server, used only by `vault/encryption-key.sh` inside the Vault container | yes | 48 hex characters |
| `VAULT_MEMBERSHIP_TOKEN` | Vault token of membership-service, which may only read `kv-v1/data/encrypt/dbkey`. `vault/encryption-key.sh` creates it when Vault starts | yes | 48 hex characters |
| `ENCRYPTION_KEY` | AES key written to Vault at `kv-v1/data/encrypt/dbkey` when Vault starts; membership-service encrypts member data with it and refuses to start without it | yes | 32 hex characters |
| `JWT_SECRET` | Signing key for the tokens membership-service issues | yes | 128 hex characters |
| `CLICKHOUSE_PASSWORD` | Password of the ClickHouse user `otel`, used by the Collector and `make smoke` | yes | 48 hex characters |
| `CLICKHOUSE_GRAFANA_PASSWORD` | Password of the ClickHouse user `grafana`, which may only read the `otel` database, used by the Grafana datasource | yes | 48 hex characters |
| `GRAFANA_ADMIN_PASSWORD` | Password of the Grafana `admin` account, the only one that may change dashboards or datasources; anonymous visitors are viewers | yes | 48 hex characters |
| `KEYCLOAK_ADMIN_PASSWORD` | Password of the Keycloak `admin` account in the master realm, which Keycloak creates on its first start | yes | 24 random characters |
| `STAFF_ADMIN_USERNAME`, `STAFF_ADMIN_PASSWORD` | The admin account the realm import creates in Keycloak | yes | `admin-` and 6 hex characters, 24 random characters |
| `STAFF_OPERATOR_USERNAME`, `STAFF_OPERATOR_PASSWORD` | The operator account the realm import creates in Keycloak | yes | `operator-` and 6 hex characters, 24 random characters |
| `STAFF_VIEWER_USERNAME`, `STAFF_VIEWER_PASSWORD` | The viewer account the realm import creates in Keycloak | yes | `viewer-` and 6 hex characters, 24 random characters |

`python3 scripts/check-env.py .` checks that `compose.yaml` and `.env.example` name the same keys, and that every variable a service receives is one the service reads without repeating its default. `make verify` runs it as the `env` step.
