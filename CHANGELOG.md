# Changelog

What each release adds and changes, newest first. Every PR into `develop` that changes behaviour adds a line to Unreleased, and `make release-branch` turns Unreleased into the new version. The format is checked by `make verify`, and the rules are in [CONTRIBUTING.md](CONTRIBUTING.md#changelog).

## [Unreleased]

### Added

- The README has a **How it is built** section: how the maintainer and the coding agents split the work, the issue and pull request flow, the `make verify` gate, the release confirmation, and where decisions, retrospectives and the scorecard live ([#172](https://github.com/chrysaor/JayPay/issues/172))
- `make scorecard` writes `docs/scorecard.md` from GitHub and the local test reports: merged PRs and the time from open to merge per release, the CI failure rate, the time between releases, line coverage, test counts, smoke scenes, and issues by label and milestone, each with its source and what it does not measure. The vitest runs of both frontend apps also write `coverage/test-results.json` ([#152](https://github.com/chrysaor/JayPay/issues/152))

### Changed

- The customer site and the admin console build on Node 26, the active LTS, in place of Node 24. Java stays on 25, and Dependabot no longer proposes a new JDK major ([#168](https://github.com/chrysaor/JayPay/issues/168))

### Fixed

- A payment whose debit answer was lost now finishes when the same request is sent again, even after the payer or the merchant stopped being valid or the merchant lost its corporate flag. Before, the repeat answered 400 and the price stayed taken from the wallet on a payment that never reached settlement. payment-service now checks the payer and the merchant only on the first request with an id, the way remittance-service does ([#136](https://github.com/chrysaor/JayPay/issues/136))
- A wallet change asked while membership-service fails or does not answer now answers 503 with `MEMBERSHIP_NOT_CONFIRMED`, records nothing under its request id, and is applied once when the same request is sent again. Before, it was stored as failed for good, so a remittance refund asked during the outage left the sender's money taken. A member that does not exist or is not valid is still refused with the status `2`, and `GET /memberships/{membershipId}` now answers 404 instead of 500 when the id names no member ([#137](https://github.com/chrysaor/JayPay/issues/137))
- A settlement payout the bank refused is now paid to the account the merchant registers next, once, by the next settlement run. Before, every run sent the same request id with the new account, banking-service answered 409 `REQUEST_ID_REUSED`, and the payment stayed `PAID` for good. settlement-service keeps each payment's payout attempt and its account in the new table `payout_attempt`, and starts a new attempt only after the bank refused the one before, so an unanswered or confirmed payout is never sent to the new account ([#138](https://github.com/chrysaor/JayPay/issues/138))
- The region total of money-query-service now counts a saga top-up only after the saga increased the wallet, and counts it once. Before, it counted every charge the bank confirmed, so a top-up the saga rolled back after its deadline stayed in the total, and a lost tracking token in the `money_query` database would have added every top-up again. money-service publishes the new `WalletRechargedEvent` after it increased the wallet, and money-query-service records each top-up under its recharge id in the same DynamoDB transaction that adds to the totals. Totals written before this release are not corrected ([#139](https://github.com/chrysaor/JayPay/issues/139))
- A Kafka consumer whose rebalance latency total turns negative no longer takes its whole service out of Prometheus. Before, every scrape of the service answered 500 with `counters cannot have a negative value` until a restart. Every service now leaves out `kafka_consumer_coordinator_rebalance_latency_total`, the one Kafka client counter that adds up wall-clock differences, and keeps `kafka_consumer_coordinator_rebalance_latency_avg`, `_max` and `kafka_consumer_coordinator_rebalance_total` ([#154](https://github.com/chrysaor/JayPay/issues/154))
- Top-up charges, bank remittances and settlement payouts now all use one company account, which banking-service defines in `banking.company-account`. Before, a charge went into one account while its `RequestFirmBankingFinishedEvent` named another, and remittances and payouts left from two more that money-service, remittance-service and settlement-service each wrote down for themselves. `POST /banking/firmbanking/request` no longer takes `fromBankName` and `fromBankAccountNumber`, ignores them when sent, and always sends from the company account. A transfer stored before this change is still answered as a repeat when its id, receiver and amount match ([#140](https://github.com/chrysaor/JayPay/issues/140))
- banking-service, money-service, payment-service, remittance-service and event-flow-service now store a status value that a later version adds on a database that 0.4.0 or an earlier version created. Before, the database kept a check constraint that listed the values of the version that created the table, so a new value was refused. Each service drops the value checks of its enum columns on every start, such as `bank_transfer_status_check`, and keeps the checks that guard amounts and balances ([#148](https://github.com/chrysaor/JayPay/issues/148))
- A saga top-up asked while banking-service fails or does not answer the account lookup now answers 503 with `REGISTERED_ACCOUNT_NOT_CONFIRMED`, records nothing under its request id, and tops up once when the same request is sent again. Before, it was stored as failed for good, as if the member had no registered account, and the member had to send a new request id. `GET /banking/account/{membershipId}` now answers 404 instead of 500 when the member has no registered account, and the customer site shows the form to link an account for that answer ([#182](https://github.com/chrysaor/JayPay/issues/182))
- A top-up rollback that banking-service refuses or does not confirm is now sent again, first after 30 seconds and then after twice the wait before, up to five sends in about 15 minutes. Before, the saga sent it once and only logged a failure, so a charge that left the member's bank account was not returned. When no send is confirmed, money-service logs an error and the top-up is stored with the new status `ROLLBACK_UNCONFIRMED`, which a repeat of the request answers as `moneyChangingResultStatus` `3`. `money.recharge.rollback.retry-after` and `money.recharge.rollback.attempts` set the first wait and the number of sends. A restart of money-service during the retries stops them ([#183](https://github.com/chrysaor/JayPay/issues/183))

## [0.4.0] - 2026-11-26

### Added

- The admin console has an **Event flow** page for every staff role. It lists the newest saga top-ups by status and shows each one step by step, with the service, outcome, time and reason of every step, and refreshes while a top-up is in progress. money-service, membership-service and banking-service write each step to the Kafka topic `jaypay.flow.events` with member ids and amounts only, and the new workload event-flow-service, with 512m of memory and its own `event_flow` database, collects them and serves `GET /flows` and `GET /flows/{flowType}/{flowId}` ([#124](https://github.com/chrysaor/JayPay/issues/124))
- The **Event flow** page also follows remittances, payments and settlements step by step, filters the list by type and shows the type of each flow. remittance-service, payment-service and settlement-service write their steps to `jaypay.flow.events` with member ids and amounts only. `GET /flows` takes `?flowType=`, and event-flow-service deletes every flow whose newest step is older than `FLOW_RETENTION_PERIOD`, 30 days by default ([#124](https://github.com/chrysaor/JayPay/issues/124))
- `CHANGELOG.md` records what each release adds. `make release-branch VERSION=X.Y.Z` moves Unreleased into the new version in the release commit, and the tag workflow publishes that section as the GitHub Release notes ([#113](https://github.com/chrysaor/JayPay/issues/113))
- `docs/decisions/` records each decision that shaped JayPay with the release it was made for, the options, what it gave up and the open decisions that wait for the owner, and `docs/news.md` sums up each release with its decisions, retrospectives and new or changed documents, and lists the next milestones ([#150](https://github.com/chrysaor/JayPay/issues/150), [#151](https://github.com/chrysaor/JayPay/issues/151))
- `make coverage` prints the line coverage of the backend, measured by JaCoCo across every module, and of the customer site and the admin console, measured by vitest. CI writes the same table to the summary of its backend and frontend jobs. The README opens with the CI, Security, release and license badges and a quick start, and JayPay is released under the MIT License ([#134](https://github.com/chrysaor/JayPay/issues/134))

### Changed

- Staff sign in to the admin console through Keycloak, which runs in both local stacks with its own `keycloak` database in the existing PostgreSQL and its admin console on `127.0.0.1:8092`. The realm `staff` holds the roles viewer, operator and admin and the first three accounts from the `STAFF_*` keys, and staff accounts are managed in the Keycloak admin console instead of the removed staff accounts page and `/auth/staff/*` endpoints. auth-service now only checks the Keycloak token for each request and has no database. `KEYCLOAK_ADMIN_PASSWORD` replaces `STAFF_JWT_SECRET` in `infra/docker/.env`; run `make up` and the accounts are imported again with the current `STAFF_*` values ([#112](https://github.com/chrysaor/JayPay/issues/112))
- The service images run a `jlink` runtime cut from the same Temurin build on a distroless glibc base with no shell or package manager. The libraries every service shares sit in their own layers, so the eleven images share them, and membership-service drops from about 485 MB to 281 MB uncompressed ([#107](https://github.com/chrysaor/JayPay/pull/107))
- The tag workflow fails before it publishes any image when `CHANGELOG.md` has no section for the version, and it retries creating the GitHub Release when GitHub answers with an error ([#113](https://github.com/chrysaor/JayPay/issues/113))
- Every money-service change takes a `requestId` in its body and answers a repeat with the first result, or 409 `REQUEST_ID_REUSED` when the same id carries a different change. `/money/decrease` lowers the balance, `/money/decrease-eda` is gone, and balances and amounts are 64-bit in money-service, remittance-service and money-aggregation-service. Wallets live in the new `wallet` table, so wallets in a local database from before start again from zero ([#96](https://github.com/chrysaor/JayPay/issues/96))

- `POST /remittance/request` takes a JSON body with a `requestId` and a whole-number `amount`, and answers with `remittanceId`, `fromMembershipId`, `toMembershipId`, `toBankName`, `toBankAccountNumber` and a `remittanceStatus` of `COMPLETED`, `FAILED`, `REFUNDED` or `TRANSFERRING`, or 503 `REMITTANCE_NOT_CONFIRMED` when the same request has to be sent again. `GET /remittance/{membershipId}` lists the remittances a member sent or received. Remittances live in the new `remittance` table, so remittances in a local database from before are no longer listed ([#98](https://github.com/chrysaor/JayPay/issues/98))
- `POST /payment/request` takes a JSON body with a `requestId` and a whole-number `requestPrice`, and answers with `fee`, `payout`, `feeRateBasisPoints` and a `paymentStatus` of `REQUESTED`, `PAID`, `FAILED` or `SETTLED`. `POST /payment/finish-settlement` answers with `alreadySettled`. Payments live in the new `payment` table, so payments in a local database from before are no longer listed or paid out ([#97](https://github.com/chrysaor/JayPay/issues/97))
- `POST /banking/firmbanking/request` takes a `requestId`, sends each transfer to the bank once per id and answers a repeat with the first result, 409 `REQUEST_ID_REUSED` for another transfer under the same id, 400 for an amount that is not a whole number from 1 to 10,000,000, and 503 `TRANSFER_NOT_CONFIRMED` when the bank did not answer. The answer carries `transferStatus` `SUCCEEDED` or `FAILED`. `/banking/firmbanking/request-eda` and `/banking/firmbanking/update-eda` are gone. Transfers live in the new `bank_transfer` table, so firm banking requests in a local database from before are no longer read ([#99](https://github.com/chrysaor/JayPay/issues/99))
- The admin console no longer has **Mark settled**, and auth-service refuses `finish-settlement` to every staff role: settlement-service settles a payment only after the bank confirmed its payout, and a later run sends the same request id again ([#99](https://github.com/chrysaor/JayPay/issues/99))
- `POST /money/increase-eda` answers with the wallet change instead of an empty body ([#99](https://github.com/chrysaor/JayPay/issues/99))
- `POST /money/increase-async` and the task-consumer workload are gone, with the Kafka topics `jaypay.task.topic` and `jaypay.task.result.topic` and the money-service keys `TASK_TOPIC` and `TASK_RESULT_TOPIC`. Top a wallet up through `POST /money/increase-eda` instead. Both local stacks run ten Spring Boot services, and Prometheus scrapes ten targets ([#99](https://github.com/chrysaor/JayPay/issues/99))
- Service-to-service HTTP calls give up after a 2 second connect timeout and a 10 second request timeout, set by `jaypay.http.connect-timeout` and `jaypay.http.request-timeout` ([#99](https://github.com/chrysaor/JayPay/issues/99))

### Fixed

- A saga top-up requested right after a new cluster starts reaches the wallet: money-service reads the recharge events from the start of the stream the first time its saga processor starts, instead of skipping the ones published before it connected to Axon Server ([#132](https://github.com/chrysaor/JayPay/issues/132))
- A saga top-up first asks membership-service whether the member exists and is valid, and fails without asking the bank when it does not. When membership-service does not answer, the 60-second deadline fails the top-up ([#99](https://github.com/chrysaor/JayPay/issues/99))
- A saga top-up adds money only when the bank confirmed the charge. An account that fails the check, a charge the bank refuses, a member without a bank account and a top-up that is not done within 60 seconds end as failed, the last one with the charge refunded, and the region total of money-query-service counts only confirmed charges ([#99](https://github.com/chrysaor/JayPay/issues/99))
- A bank remittance the bank did not answer answers 503 and finishes when the same request is sent again, with the transfer sent once, and a refused one gives the money back ([#99](https://github.com/chrysaor/JayPay/issues/99))
- Opening a wallet waits for its aggregate and reports a failure, and a saga top-up for a member without a wallet aggregate creates one instead of leaving the wallet without it ([#99](https://github.com/chrysaor/JayPay/issues/99))
- Settlement pays each payment out once with integer arithmetic: payment-service stores the fee, rounded down to the won, and the payout, which always add up to the price, and settlement-service pays a payment only after its own `PAID` to `SETTLED` update succeeded, so repeated or concurrent runs pay nothing twice ([#97](https://github.com/chrysaor/JayPay/issues/97))
- A wallet balance stays correct: money-service refuses an amount that is not a whole number from 1 to `MONEY_AMOUNT_MAX` (10,000,000 by default) with 400 and changes nothing, a decrease never takes a balance below zero, each member has one wallet, and concurrent or repeated requests count exactly once ([#96](https://github.com/chrysaor/JayPay/issues/96))
### Security

- Staff sign-in uses the Authorization Code flow with PKCE, locks an account after 5 wrong passwords, refuses passwords from a list of common ones, the username or the last 5 passwords, and offers an authenticator app. The console keeps its tokens in memory only, an access token lasts 2 minutes, and signing out ends the Keycloak session ([#112](https://github.com/chrysaor/JayPay/issues/112))
- A remittance takes the amount from the sender's wallet once and gives it to the receiver once, sends one bank transfer, or refunds the sender. It no longer tops up a short wallet, a bank remittance now debits the sender first, and an amount that is not a whole number from 1 to `REMITTANCE_AMOUNT_MAX` (10,000,000 by default), a remittance to the sender and an unknown receiver are refused with 400 ([#98](https://github.com/chrysaor/JayPay/issues/98))
- A payment takes its price from the payer's wallet once, records `FAILED` when the balance is too low, refuses a price that is not a whole number from 1 to `PAYMENT_AMOUNT_MAX` (10,000,000 by default) and a merchant that is not a valid corporate member, and ignores a fee rate in the request ([#97](https://github.com/chrysaor/JayPay/issues/97))
- The customer site forwards only the 11 requests its screens make and answers 404 to every other `/api/` path, so the services' internal endpoints, Actuator and the OpenAPI pages no longer answer through it ([#110](https://github.com/chrysaor/JayPay/pull/110))
- The admin site picks the upstream from the normalized path, so a path with an encoded `..` such as `/api/membership/..%2Fauth/staff/me` no longer reaches membership-service without the staff check ([#110](https://github.com/chrysaor/JayPay/pull/110))
- Actuator moved to the management port 9080 of every service and exposes only health, the liveness and readiness probes and the Prometheus metrics. The service ports answer `/actuator/**` like any unknown path, no host port publishes the management port, the probes and the Prometheus scrape use it, and in the cluster a NetworkPolicy admits only Prometheus on it. springdoc is off unless a service runs with the Spring profile `local`, so `/v3/api-docs` and Swagger UI are gone from the service ports ([#111](https://github.com/chrysaor/JayPay/issues/111))
- membership-service refuses to start when Vault does not answer or holds no encryption key, instead of encrypting with `ENCRYPTION_FALLBACK_KEY`, which is gone. It reads the key with the new `VAULT_MEMBERSHIP_TOKEN`, which may only read that key, instead of the Vault root token. Vault writes the key again each time its container starts, so a restart of Vault or of Docker no longer leaves it empty. Anonymous Grafana visitors are viewers instead of editors, the login form is back for `admin`, and Grafana queries ClickHouse as the new user `grafana`, which may only read the `otel` database, with `CLICKHOUSE_GRAFANA_PASSWORD`. Kafbat UI is read-only. Every container of both local stacks runs as a non-root user with every capability dropped and no privilege escalation, and every one but Keycloak with a read-only root file system; DynamoDB Local no longer runs as root. Run `python3 scripts/init-env.py` before `make up` to add the two new keys ([#101](https://github.com/chrysaor/JayPay/issues/101))

## [0.3.0] - 2026-11-26

### Added

- Helm charts for every service and both sites, with values per environment under `deploy/envs`. `make verify` lints and renders them, checks them against the Kubernetes schema, and fails when `deploy/envs/local` and `compose.yaml` disagree ([#78](https://github.com/chrysaor/JayPay/pull/78))
- `make k8s-up` runs the whole stack on a local minikube cluster, with a chart for each dependency and the sites on `web.jaypay.localhost` and `admin.jaypay.localhost`, and `make k8s-smoke` drives it end to end ([#80](https://github.com/chrysaor/JayPay/pull/80))
- Argo CD syncs the local cluster from `deploy/envs/local` through an ApplicationSet that makes one Application per workload. `make k8s-gitops-check` proves that it follows a commit and reverts a manual change ([#82](https://github.com/chrysaor/JayPay/pull/82))
- The local cluster runs the OpenTelemetry Collector, Prometheus and Grafana, and a NetworkPolicy that denies by default and allows only the calls the workloads make. `make k8s-policy-check` probes every Service port from every pod ([#87](https://github.com/chrysaor/JayPay/pull/87))
- DynamoDB Local keeps its data across a restart, and one `table.json` defines the table for Terraform, the local stack and the tests ([#76](https://github.com/chrysaor/JayPay/pull/76))
- `docs/components.md` lists every component and the file that pins its version ([#85](https://github.com/chrysaor/JayPay/pull/85))
- `docs/services.md` describes every service and site, and `make diagrams` draws the README diagrams from the code and the deployment files. `make verify` fails when either disagrees with them ([#104](https://github.com/chrysaor/JayPay/pull/104))

### Changed

- Dependabot patch and minor updates into `develop` merge by themselves once the required checks pass, and Dependabot waits 5 days after an upstream release before it proposes it ([#92](https://github.com/chrysaor/JayPay/pull/92))
- lz4-java 1.14.1 in the backend and `@types/node` 26 in the frontend ([#66](https://github.com/chrysaor/JayPay/pull/66), [#67](https://github.com/chrysaor/JayPay/pull/67))
- The README's known limitations name the open findings of the security review ([#95](https://github.com/chrysaor/JayPay/issues/95), [#96](https://github.com/chrysaor/JayPay/issues/96), [#97](https://github.com/chrysaor/JayPay/issues/97), [#98](https://github.com/chrysaor/JayPay/issues/98))

### Security

- Both sites run nginx 1.31.10 from the official image, pinned by digest, instead of an unprivileged image that stayed on 1.31.7. 1.31.10 fixes the `resolver` use-after-free (CVE-2026-123634), and both sites set `resolver` ([#105](https://github.com/chrysaor/JayPay/pull/105))

## [0.2.0] - 2026-11-25

### Added

- auth-service holds staff accounts with the roles viewer, operator and admin, checks passwords against one set of rules, signs staff in with a staff token, and keeps an MFA flag that an admin can set ([#70](https://github.com/chrysaor/JayPay/pull/70))
- The admin site signs staff in, hides what a role may not do and manages staff accounts. Its nginx asks auth-service to authorise every API request ([#70](https://github.com/chrysaor/JayPay/pull/70))
- `make up` generates one default staff account per role from environment variables ([#70](https://github.com/chrysaor/JayPay/pull/70))

## [0.1.0] - 2026-11-25

### Added

- A customer site to sign up, sign in, top up the wallet, register a bank account, send money and pay, and an admin console to look up and edit members, read regional money totals and mark payments as settled ([#52](https://github.com/chrysaor/JayPay/pull/52))
- A local Docker stack behind `make up`, `make smoke` and `make down`. Logs and traces go through OpenTelemetry into ClickHouse, metrics into Prometheus, and Grafana starts with three dashboards ([#54](https://github.com/chrysaor/JayPay/pull/54))
- Terraform for the DynamoDB table that money-query-service reads and writes ([#43](https://github.com/chrysaor/JayPay/pull/43))
- `make verify`, one gate that CI runs too, and a release workflow that publishes `X.Y.Z-rc.<sha>` images from a release branch and final images and a GitHub Release from a `vX.Y.Z` tag ([#48](https://github.com/chrysaor/JayPay/pull/48))
- CI, CodeQL, dependency review, Trivy and gitleaks scans, and weekly Dependabot updates ([#45](https://github.com/chrysaor/JayPay/pull/45))
- A README that describes the current architecture, with the original one kept in `docs/legacy/` ([#55](https://github.com/chrysaor/JayPay/pull/55))

### Changed

- The backend lives in `backend/` and runs on Java 25, Spring Boot 4 and Gradle 9, with PostgreSQL instead of MySQL ([#44](https://github.com/chrysaor/JayPay/pull/44))
- Every service follows the hexagonal layering, and ArchUnit rules fail the build when a service breaks it ([#57](https://github.com/chrysaor/JayPay/pull/57))
- The repository is in English, and `make verify` fails on Hangul text ([#51](https://github.com/chrysaor/JayPay/pull/51), [#52](https://github.com/chrysaor/JayPay/pull/52))

### Fixed

- `/money/increase` no longer fails on every call, five services parse member responses again, and a synchronous bank account registration stores its aggregate id ([#57](https://github.com/chrysaor/JayPay/pull/57))
- A payment calls membership-service on the right path ([#54](https://github.com/chrysaor/JayPay/pull/54))

### Security

- Secrets come only from environment variables. The Vault token, AES key and JWT secret that were committed are gone from the tree, and the history scan allowlists them as rotated ([#44](https://github.com/chrysaor/JayPay/pull/44), [#60](https://github.com/chrysaor/JayPay/pull/60))
- Member emails are encrypted with AES-GCM instead of ECB, and account numbers come from `SecureRandom`. Rows written in ECB mode can no longer be read, so an old Postgres volume needs a reset ([#56](https://github.com/chrysaor/JayPay/pull/56))
- Member responses no longer carry `refreshToken` ([#57](https://github.com/chrysaor/JayPay/pull/57))
