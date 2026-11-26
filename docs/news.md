# News

What each release was about, newest first: the theme, the changes that matter, the [decisions](decisions/README.md) made for it, the [retrospectives](retrospectives/README.md) written in it, and the documents it added or changed. [CHANGELOG.md](../CHANGELOG.md) has every change line by line.

## In progress and next

| Milestone | Theme | Issues |
| --- | --- | --- |
| 0.5.0 | Money correctness: flows that fail between two services finish, and the company account has one owner | [#136](https://github.com/chrysaor/JayPay/issues/136), [#137](https://github.com/chrysaor/JayPay/issues/137), [#138](https://github.com/chrysaor/JayPay/issues/138), [#139](https://github.com/chrysaor/JayPay/issues/139), [#140](https://github.com/chrysaor/JayPay/issues/140), [#142](https://github.com/chrysaor/JayPay/issues/142), [#148](https://github.com/chrysaor/JayPay/issues/148), [#152](https://github.com/chrysaor/JayPay/issues/152), [#154](https://github.com/chrysaor/JayPay/issues/154), [#168](https://github.com/chrysaor/JayPay/issues/168) |
| 0.6.0 | Confidence: the smoke in CI, browser tests and the audit of missing tests | [#143](https://github.com/chrysaor/JayPay/issues/143), [#144](https://github.com/chrysaor/JayPay/issues/144), [#149](https://github.com/chrysaor/JayPay/issues/149) |

The decisions that wait for the owner are listed under [Open decisions](decisions/README.md#open-decisions).

## 0.4.0 (2026-11-26)

Money moves correctly, staff sign in through Keycloak, and operators can watch every money flow step by step. The money and security fixes answer the security review in [#88](https://github.com/chrysaor/JayPay/issues/88).

### What changed

- Wallets, payments, remittances and bank transfers take a request id, count a repeated request once, refuse bad amounts, and move money exactly once or give it back.
- A saga top-up checks the member first and adds money only after the bank confirmed the charge. The Kafka task top-up and the task-consumer workload are gone.
- The admin console has an **Event flow** page that follows top-ups, remittances, payments and settlements step by step, fed by the new event-flow-service.
- Staff sign in through Keycloak, with lockout, a password policy and an optional authenticator app. auth-service only checks the token.
- The customer site forwards only the requests its screens make, Actuator moved to a management port, membership-service fails closed when Vault has no key, and every local container runs unprivileged.
- The service images run a `jlink` runtime on distroless.
- `CHANGELOG.md` is kept by the gate and becomes the Release notes, `make coverage` prints the line coverage, the README has badges and a quick start, and JayPay is under the MIT License.

### Decisions

- [0009](decisions/0009-changelog-checked-by-the-gate.md) A changelog that the gate keeps current and the release publishes
- [0010](decisions/0010-staff-sign-in-through-keycloak.md) Staff sign in through Keycloak
- [0011](decisions/0011-kafka-for-events-not-tasks.md) Kafka stays for events, and the Kafka task top-up goes
- [0012](decisions/0012-event-flow-service-and-topic.md) Event flow as its own service fed by a Kafka topic
- [0013](decisions/0013-saga-first-token-at-stream-start.md) The saga processor's first token starts at the beginning of the stream
- [0014](decisions/0014-flow-type-and-step-stored-as-text.md) Event flow stores its flow type and step as text
- [0015](decisions/0015-coverage-without-an-external-service.md) Test coverage measured by JaCoCo and vitest, without an external service

### Retrospectives

- [A top-up asked right after a first start was never processed](retrospectives/2026-11-26-saga-skips-events-on-first-start.md)
- [An existing event flow database refused the steps of new flows](retrospectives/2026-11-26-event-flow-schema-refuses-new-steps.md)
- [Two tests that passed or failed by timing](retrospectives/2026-11-26-tests-that-depend-on-timing.md)
- [event-flow-service was killed at its memory limit on the cluster](retrospectives/2026-11-26-event-flow-service-killed-at-its-memory-limit.md)

### Documents

- Added: [docs/news.md](news.md), [docs/decisions/](decisions/README.md), [docs/retrospectives/](retrospectives/README.md), and the screenshots in `docs/images/`
- Updated: [README.md](../README.md), [CLAUDE.md](../CLAUDE.md), [CONTRIBUTING.md](../CONTRIBUTING.md), [docs/api-contract.md](api-contract.md), [docs/architecture.md](architecture.md), [docs/components.md](components.md), [docs/services.md](services.md), and the four diagrams in `docs/diagrams/`

## 0.3.0 (2026-11-26)

The whole stack runs on a local Kubernetes cluster synced by Argo CD, and every service has a reference page.

### What changed

- Helm charts for every workload, checked by the gate against the Kubernetes schema and against `compose.yaml`.
- `make k8s-up` runs the stack on minikube, Argo CD syncs it from `deploy/envs/local`, and the cluster runs the observability stack and a default-deny NetworkPolicy. Each step has a check that can be rerun.
- DynamoDB Local keeps its data, and one `table.json` defines the table everywhere.
- `docs/services.md` describes every service, `make diagrams` draws the README diagrams from the code, and the gate fails when either is out of date.
- Dependabot patch and minor updates merge by themselves once the checks pass.
- Both sites run nginx 1.31.10 from the official image, pinned by digest.

### Decisions

- [0005](decisions/0005-tools-and-images-pinned-by-digest.md) Scanners, tools and base images pinned by digest, extended to the site images
- [0007](decisions/0007-kubernetes-and-gitops-foundation.md) Kubernetes and GitOps as the target, with only the foundation in this repository
- [0008](decisions/0008-dependabot-merges-patch-and-minor.md) Dependabot patch and minor updates merge by themselves

### Retrospectives

None.

### Documents

- Added: [docs/services.md](services.md), [docs/components.md](components.md), and the four SVG diagrams in `docs/diagrams/`
- Updated: [README.md](../README.md), [CLAUDE.md](../CLAUDE.md), [CONTRIBUTING.md](../CONTRIBUTING.md), [docs/architecture.md](architecture.md)

## 0.2.0 (2026-11-25)

Staff sign in to the admin console, and every console request is checked against their role.

### What changed

- auth-service holds staff accounts with the roles viewer, operator and admin, one set of password rules and its own staff token.
- The admin site signs staff in, hides what a role may not do and manages staff accounts, and its nginx asks auth-service before forwarding any API request.
- `make up` creates one staff account per role from environment variables.

### Decisions

- [0006](decisions/0006-staff-sign-in-in-auth-service.md) Staff sign-in written in a new auth-service, superseded in 0.4.0

### Retrospectives

None.

### Documents

- Updated: [README.md](../README.md), [docs/api-contract.md](api-contract.md), [docs/architecture.md](architecture.md)

## 0.1.0 (2026-11-25)

The first release of the rebuild: the 2023 project on current Java and Spring, two React sites, a local stack with logs, traces and metrics, and one gate that CI runs too.

### What changed

- The backend runs on Java 25, Spring Boot 4 and Gradle 9 with PostgreSQL, and ArchUnit holds every service to the hexagonal layers.
- A customer site and an admin console.
- `make up`, `make smoke` and `make down` for a local Docker stack, with OpenTelemetry, ClickHouse, Prometheus and Grafana.
- `make verify`, CI, the security scans, Dependabot, and a release workflow that publishes candidate and final images.
- Secrets come only from environment variables, member emails are encrypted with AES-GCM, and the repository is in English.

### Decisions

- [0001](decisions/0001-java-25-and-spring-boot-4.md) Rebuild the backend on Java 25, Spring Boot 4 and PostgreSQL
- [0002](decisions/0002-hexagonal-layers-checked-by-archunit.md) Hexagonal layers in every service, checked by ArchUnit
- [0003](decisions/0003-one-gate-for-local-runs-and-ci.md) One gate, `make verify`, for local runs and CI
- [0004](decisions/0004-branch-flow-and-owner-confirmed-releases.md) `master`, `develop` and release branches, with releases confirmed by the owner
- [0005](decisions/0005-tools-and-images-pinned-by-digest.md) Scanners, tools and base images pinned by digest

### Retrospectives

None.

### Documents

- Added: [CLAUDE.md](../CLAUDE.md), [CONTRIBUTING.md](../CONTRIBUTING.md), [docs/api-contract.md](api-contract.md), [docs/architecture.md](architecture.md), [docs/legacy/README.md](legacy/README.md)
- Updated: [README.md](../README.md), rebuilt around the current architecture. The documents from before the rebuild moved to `docs/legacy/`
