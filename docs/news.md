# News

What each release was about, newest first: the theme, the changes that matter, the [decisions](decisions/README.md) made for it, the [retrospectives](retrospectives/README.md) written in it, and the documents it added or changed. [CHANGELOG.md](../CHANGELOG.md) has every change line by line.

## In progress and next

| Milestone | Theme | Issues |
| --- | --- | --- |
| 0.7.0 | API gateway: the Kubernetes Gateway API with Envoy Gateway in front of the services, which checks tokens, routes, limits request rates and logs, and authentication for the customer site | [#115](https://github.com/chrysaor/JayPay/issues/115), [#91](https://github.com/chrysaor/JayPay/issues/91) |
| 0.8.0 | Payment domain: merchant, ledger and settlement record, option B of [#135](https://github.com/chrysaor/JayPay/issues/135), steps 1 to 3 | [#135](https://github.com/chrysaor/JayPay/issues/135), [#161](https://github.com/chrysaor/JayPay/issues/161), [#162](https://github.com/chrysaor/JayPay/issues/162), [#163](https://github.com/chrysaor/JayPay/issues/163) |

The decisions that wait for the owner are listed under [Open decisions](decisions/README.md#open-decisions).

## 0.6.0 (2026-11-28)

Confidence in what is there. CI starts the whole stack and runs the smoke and browser tests on every push to `develop`, an audit lists which behaviour of each use case has a test, and the faults that the audit and the 0.5.0 checks found are fixed.

### What changed

- CI runs `make smoke` and `make e2e` against the Compose stack on every push to `develop`, and a release publishes images only after the same smoke passed.
- `make e2e` drives the customer site and the admin console in Chromium with Playwright.
- `docs/test-audit.md` lists every use case against the normal path, the refusals, a repeated request, a lost answer and an outage, with the test that covers each or none.
- An outage of membership-service or banking-service answers 503 for a payment, a remittance, a bank account registration and the sum of balances too, and changes nothing, so the same request works once the service is back.
- A top-up handled while membership-service fails is counted in the region total when the service answers within 15 seconds, and an `ERROR` names the top-up that was left out when it does not.
- The sign-in endpoints of membership-service refuse with 401 or 404 and an `error` in place of 500 or an empty 200, and `POST /memberships/auth` answers the member of an access token.
- `GET /banking/account/{membershipId}` answers the account the member registered last.
- PostgreSQL has a memory limit of 512 MiB and each service keeps three connections, and the local cluster node starts with 14 GiB.

### Upgrading from 0.5.0

- A caller that read an empty 200 from `POST /memberships/login`, `/memberships/refresh-token` or `/memberships/auth` as a refusal now gets 401 `MEMBERSHIP_NOT_VALID` or `TOKEN_REFUSED`. The customer site handles both answers, so it works next to a membership-service of either release.
- A payment, a remittance and a bank account registration can now answer 503 `MEMBERSHIP_NOT_CONFIRMED` or `BANK_ACCOUNT_NOT_CONFIRMED`. Send the same request again later.
- Each service opens three PostgreSQL connections in place of ten. `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` raises it for a service.
- `make k8s-up` needs a Docker VM with at least 14 GiB of memory.

### Decisions

- [0032](decisions/0032-smoke-in-ci-on-pushes-not-pull-requests.md) CI runs the smoke against the Compose stack on pushes to `develop` and releases, not on pull requests
- [0033](decisions/0033-three-postgres-connections-per-service.md) Each service keeps three PostgreSQL connections, and the memory limit of PostgreSQL stays
- [0034](decisions/0034-postgres-memory-limit-of-512-mib.md) PostgreSQL gets a memory limit of 512 MiB
- [0035](decisions/0035-an-outage-of-a-checked-service-answers-503.md) A request whose member or bank account cannot be checked answers 503 and leaves no flow step
- [0036](decisions/0036-region-total-asks-for-the-address-for-a-fixed-time.md) The region total asks for a member's address for a fixed time, then leaves the top-up out
- [0037](decisions/0037-sign-in-refusals-answer-401.md) The sign-in endpoints refuse with 401, and an access token names its member
- [0038](decisions/0038-cold-start-restarts-are-accepted.md) Restarts on a cold start of the cluster are accepted
- [0039](decisions/0039-no-mutation-testing-for-now.md) No mutation testing for now

### Retrospectives

- [A member who registered another bank account kept the first one](retrospectives/2026-11-28-account-lookup-kept-the-first-registered-account.md)
- [The other callers still read an outage as a refusal](retrospectives/2026-11-28-other-callers-read-an-outage-as-a-refusal.md)
- [A top-up handled during an outage was left out of the region total](retrospectives/2026-11-28-top-up-during-an-outage-left-out-of-the-region-total.md)

### Documents

- Added: [docs/test-audit.md](test-audit.md)
- Updated: [README.md](../README.md), [CONTRIBUTING.md](../CONTRIBUTING.md), [deploy/README.md](../deploy/README.md), [docs/architecture.md](architecture.md), [docs/components.md](components.md), [docs/services.md](services.md)

## 0.5.0 (2026-11-27)

Money stays correct when something fails between two services. A top-up, a payment, a remittance or a payout that was not confirmed now finishes by itself, and an outage is no longer read as a refusal.

### What changed

- Payments and remittances that were not confirmed finish without the caller: a sweep in payment-service and in remittance-service asks again and completes or refunds them.
- A saga top-up survives a restart of money-service, sends a rollback again when the bank does not confirm it, and ends the rollback only on a refund the bank confirmed.
- An outage of membership-service or banking-service answers 503 and records nothing, so the same request works once the service is back.
- A remittance refund lands whatever happened to the sender since, and a refused payout is paid to the account the merchant registers next.
- The company bank account has one owner, banking-service, and the region total counts a top-up once, after the wallet increased.
- A release does not depend on the order in which the services are upgraded. The rule for messages between releases is in `CONTRIBUTING.md`.
- Log levels follow what the operator has to do, the sites build on Node 26, and `make scorecard` writes `docs/scorecard.md`.
- Five checks of the gate, the smoke test and CI now fail when the thing they guard breaks.

### Upgrading from 0.4.0

- The services can be upgraded in any order. While banking-service still runs 0.4.0, a remittance to a bank account ends `REFUNDED` and a settlement payout waits for the next run. No money is lost. The `ERROR` that settlement-service logs about the merchant's account in that window needs no action.
- Right after the upgrade, the sweeps complete or refund the payments and remittances that 0.4.0 left unfinished.

### Decisions

- [0016](decisions/0016-java-25-and-node-26-lts.md) Java stays on 25 and the sites build on Node 26, the LTS lines
- [0017](decisions/0017-payment-resumes-before-checking-parties.md) A repeated payment resumes before the parties are checked
- [0018](decisions/0018-membership-outage-is-not-a-missing-member.md) A membership-service outage is not a missing member
- [0019](decisions/0019-payout-attempts-in-settlement-service.md) settlement-service keeps a payout attempt for each payment
- [0020](decisions/0020-company-account-owned-by-banking-service.md) banking-service owns the company bank account
- [0021](decisions/0021-region-total-counts-wallet-increases.md) The region total counts wallet increases, once per recharge
- [0022](decisions/0022-enum-columns-without-value-checks.md) Enum columns keep no check of their values, without a migration tool
- [0023](decisions/0023-kafka-rebalance-latency-total-left-out.md) The Kafka rebalance latency total is left out of the metrics
- [0024](decisions/0024-log-levels-by-what-the-operator-does.md) Log levels follow what the operator has to do
- [0025](decisions/0025-top-up-rollback-sent-again-by-the-saga.md) The saga sends a top-up rollback again, a set number of times
- [0026](decisions/0026-lost-top-up-deadlines-found-by-a-sweep.md) A sweep finds top-up sagas whose deadline a restart dropped
- [0027](decisions/0027-unconfirmed-payments-finished-by-a-sweep.md) A sweep finishes payments that money-service did not confirm
- [0028](decisions/0028-top-up-rollback-ends-on-a-confirmed-refund.md) A top-up rollback ends only on a refund the bank confirmed
- [0029](decisions/0029-refund-passes-whatever-the-member-state.md) money-service gives back money it took, whatever the state of the member
- [0030](decisions/0030-releases-do-not-depend-on-the-order-of-the-services.md) A release does not depend on the order in which the services are upgraded
- [0031](decisions/0031-unfinished-remittances-finished-by-a-sweep.md) A sweep finishes remittances that were not confirmed

### Retrospectives

- [A top-up asked during a banking-service outage failed for good](retrospectives/2026-11-27-banking-outage-read-as-missing-account.md)
- [Checks that passed without checking](retrospectives/2026-11-27-checks-that-passed-without-checking.md)
- [The company bank account had four definitions in four services](retrospectives/2026-11-27-company-account-defined-in-four-services.md)
- [The first-start saga test stopped the processor before its worker ran](retrospectives/2026-11-27-first-start-saga-test-failed-without-a-reason.md)
- [A wallet change asked during a membership-service outage failed for good](retrospectives/2026-11-27-membership-outage-read-as-missing-member.md)
- [One negative Kafka counter took logging-consumer out of Prometheus](retrospectives/2026-11-27-negative-kafka-counter-failed-every-scrape.md)
- [A payment whose debit answer was lost could never finish](retrospectives/2026-11-27-payment-checks-parties-before-resuming.md)
- [A remittance refund asked after the sender became invalid never landed](retrospectives/2026-11-27-refund-refused-for-an-invalid-sender.md)
- [A refused payout could not be sent again after the merchant changed the account](retrospectives/2026-11-27-refused-payout-bound-to-one-request-id.md)
- [The region total kept top-ups the saga rolled back](retrospectives/2026-11-27-region-total-kept-rolled-back-top-ups.md)
- [The 0.4.0 release branch failed its own checks three times](retrospectives/2026-11-27-release-branch-failed-its-own-checks.md)
- [A restart of money-service dropped the deadlines of running top-ups](retrospectives/2026-11-27-saga-deadlines-lost-on-restart.md)
- [The status columns of four released services would refuse a value added later](retrospectives/2026-11-27-status-columns-refuse-later-values.md)
- [A top-up rollback ended while the bank had not confirmed the refund](retrospectives/2026-11-27-top-up-rollback-ended-on-an-unconfirmed-refund.md)
- [A top-up rollback that failed was never sent again](retrospectives/2026-11-27-top-up-rollback-sent-once.md)
- [The upgrade to 0.5.0 depended on the order of the services](retrospectives/2026-11-27-upgrade-depended-on-the-order-of-the-services.md)

### Documents

- Added: [docs/scorecard.md](scorecard.md)
- Updated: [README.md](../README.md), [CONTRIBUTING.md](../CONTRIBUTING.md), [docs/api-contract.md](api-contract.md), [docs/architecture.md](architecture.md), [docs/services.md](services.md)


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
