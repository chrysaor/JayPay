# Security

## Reporting a vulnerability

Report vulnerabilities privately through GitHub's "Report a vulnerability" button on the Security tab. Do not open a public issue.

## Automated checks

| Check | Workflow | Runs on |
| --- | --- | --- |
| CodeQL (`java-kotlin`, `javascript-typescript`) | `security.yml` | PR, push to a long-lived branch, weekly |
| Dependency review | `security.yml` | PR |
| Trivy filesystem scan (vulnerabilities and misconfigurations, SARIF to code scanning) | `security.yml` | PR, push to a long-lived branch, weekly |
| gitleaks secret scan | `security.yml` | PR (PR commits only), push to a long-lived branch (pushed commits only), weekly (full history) |
| Trivy image scan of every service image and both frontend images, fails on HIGH or CRITICAL | `release.yml` | Push to `release/**`, push of a `v*` tag |
| Dependabot version updates (gradle, npm, github-actions, docker, docker-compose, terraform) | `dependabot.yml` | Weekly, grouped, PRs into `develop`. A version update waits until the release is 5 days old, a security update does not wait |
| Dependabot auto-merge | `dependabot-auto-merge.yml` | On every Dependabot PR. Patch and minor updates merge into `develop` once the required checks pass, major updates wait for a person |
| `make verify` steps: contract diff, English-only and env checks, terraform, actionlint, Helm charts, backend, frontend | `ci.yml` | PR and push to a long-lived branch; backend and frontend only when they change |

Long-lived branches are `master`, `develop` and `release/**`.

The weekly run is Monday 03:00 KST.

## Admin console access

The admin console requires a staff account in the Keycloak realm `staff`. Keycloak stores the passwords, signs staff in with the Authorization Code flow and PKCE, and locks an account for a minute after 5 wrong passwords in a row, longer with each further failure up to 15 minutes. nginx asks auth-service about every `/api` request before it forwards it, and auth-service checks the Keycloak token's signature, issuer, audience and expiry. The roles viewer, operator and admin, the decision table and the password policy are described in [infra/docker/README.md](infra/docker/README.md#staff-accounts) and [docs/architecture.md](docs/architecture.md#admin-console-sign-in-through-keycloak).

The realm file `infra/docker/keycloak/import/staff.json` holds no password or secret. The first three accounts and their passwords come from `STAFF_*` keys in `infra/docker/.env`, and the Keycloak `admin` password from `KEYCLOAK_ADMIN_PASSWORD`. `scripts/init-env.py` generates them and git ignores the file. The repository holds no default username or password. The Keycloak admin console listens on `127.0.0.1:8092` only and is not proxied by the admin site.

What the gate does not cover:

- The service ports `127.0.0.1:8081` to `8089` reach the services without a staff token. They serve neither Actuator nor the OpenAPI pages. Actuator exposes only health and the Prometheus metrics, on the management port 9080. No host port publishes it, but in Compose every container on the network reaches it. In the cluster a NetworkPolicy admits only Prometheus, and the kubelet probes it. springdoc is off unless a service runs with the Spring profile `local`.
- The customer site on 8090 forwards only the requests its screens make, listed in `frontend/apps/web/nginx.conf.template`, and answers 404 to every other `/api/` path. It forwards those without a login, and a member id is all that sign-in asks for.
- An authenticator app is optional for staff accounts. Requiring it is one line in the realm, the `CONFIGURE_TOTP` required action, which `make smoke` would then have to answer.
- An access token stays valid for 2 minutes and auth-service accepts 60 seconds of clock skew. Disabling an account stops its sign-in and refresh at once, but its access token keeps working for up to 3 minutes. Signing out ends the Keycloak session.
- The local stacks serve Keycloak over plain HTTP, and the brute force lockout counts failures per account, not per address.

## Local stacks

Both local stacks run every container as a numeric non-root user with every capability dropped, no privilege escalation and, except Keycloak, a read-only root file system. Compose sets `no-new-privileges` and the cluster sets `seccompProfile: RuntimeDefault`. Every host port binds to `127.0.0.1`. `make verify` fails when Compose or the cluster loses one of these settings or when they differ, and `make smoke` and `make k8s-smoke` fail when a container runs as root or a port is reachable beyond the loopback address. [infra/docker/README.md](infra/docker/README.md#container-privileges) lists what each container may write.

- membership-service reads its encryption key from Vault at startup with `VAULT_MEMBERSHIP_TOKEN`, which may read that key and nothing else. It fails to start when Vault does not answer, refuses the token or holds no key, so it never encrypts with another key.
- Anonymous Grafana visitors are viewers, and only `admin` with `GRAFANA_ADMIN_PASSWORD` may change dashboards, datasources or users. Grafana reads ClickHouse as `grafana`, which may only read the `otel` database. Kafka UI is read-only.

What this does not cover:

- Vault runs in dev mode: unsealed, in memory, over plain HTTP, with the root token in `infra/docker/.env`. A production Vault needs storage, unsealing, TLS and an auth method per service instead of a fixed token.
- A viewer in Grafana can still run any `SELECT` on `otel` and any PromQL query through Explore, so anyone on the machine reads every log and trace.
- Kafka, Axon Server and DynamoDB Local take any client on their loopback ports, and inside the Compose network every container reaches them ([#100](https://github.com/chrysaor/JayPay/issues/100)).
- Keycloak keeps a writable root file system.

## Payments and settlement

A payment takes its price from the paying member's wallet once, through money-service with a request id derived from the payment, and the server sets the fee. The merchant must be a valid corporate member, and the price is a whole number from 1 to `PAYMENT_AMOUNT_MAX`. A paid payment moves to settled once, so settlement-service pays it out at most once. [docs/services.md](docs/services.md#payment-service) has the rules and the rounding.

What this does not cover:

- The paying member comes from the request body, so anyone who reaches the customer site can pay from any member's wallet ([#91](https://github.com/chrysaor/JayPay/issues/91)).
- payment-service trusts any caller of money-service and membership-service, and they trust it, with no service-to-service authentication ([#100](https://github.com/chrysaor/JayPay/issues/100)).
- A payout that the bank refuses or does not answer is logged, not retried.

## Remittances

A remittance takes the amount from the sender's wallet once and either gives it to the receiving member once, sends one bank transfer, or gives it back to the sender. Each move goes through money-service with a request id derived from the remittance, and the client's `requestId` makes a repeated request answer with the first result. The amount is a whole number from 1 to `REMITTANCE_AMOUNT_MAX`, and the receiver must be a valid member other than the sender or an account the bank accepts. [docs/services.md](docs/services.md#remittance-service) has the statuses and what finishes on its own.

What this does not cover:

- The sender comes from the request body, so anyone who reaches the customer site can send money from any member's wallet ([#91](https://github.com/chrysaor/JayPay/issues/91)).
- remittance-service trusts any caller of money-service, membership-service and banking-service, and they trust it, with no service-to-service authentication ([#100](https://github.com/chrysaor/JayPay/issues/100)).
- banking-service sends a bank transfer once per request id, and a repeated request asks the bank again under the same id. That is safe only while the bank deduplicates by that id. The fake bank keeps its memory in the process. A transfer the bank does not answer waits until the same request is sent again.
- No staff role may settle a payment by hand. settlement-service settles a payment only after the bank confirmed its payout.
- The log topic carries the name of the called method and none of its arguments, and the saga, the events and the bank transfer leave account numbers out of their log text.

## Known findings

`.github/.gitleaksignore` lists findings that stay in git history after the secret was removed and rotated. Each entry is a single gitleaks fingerprint, so the rule keeps catching every other match. Add an entry only after rotating the secret.

## Running the checks locally

From the repository root, `make scan` runs both of these:

```sh
docker run --rm -v "$PWD:/repo" -w /repo aquasec/trivy:0.76.2 fs --scanners vuln,misconfig .
docker run --rm -v "$PWD:/repo" ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo --redact --verbose --gitleaks-ignore-path /repo/.github/.gitleaksignore
```

The gitleaks command scans full history, like the weekly run. Add `--log-opts=origin/develop..HEAD` to scan only your branch, like a PR run.

`make images scan-images` builds every image and runs the same image scan as `release.yml`.
