# Changelog

What each release adds and changes, newest first. Every PR into `develop` that changes behaviour adds a line to Unreleased, and `make release-branch` turns Unreleased into the new version. The format is checked by `make verify`, and the rules are in [CONTRIBUTING.md](CONTRIBUTING.md#changelog).

## [Unreleased]

### Added

- `CHANGELOG.md` records what each release adds. `make release-branch VERSION=X.Y.Z` moves Unreleased into the new version in the release commit, and the tag workflow publishes that section as the GitHub Release notes ([#113](https://github.com/chrysaor/JayPay/issues/113))

### Changed

- The service images run a `jlink` runtime cut from the same Temurin build on a distroless glibc base with no shell or package manager. The libraries every service shares sit in their own layers, so the eleven images share them, and membership-service drops from about 485 MB to 281 MB uncompressed ([#107](https://github.com/chrysaor/JayPay/pull/107))
- The tag workflow fails before it publishes any image when `CHANGELOG.md` has no section for the version, and it retries creating the GitHub Release when GitHub answers with an error ([#113](https://github.com/chrysaor/JayPay/issues/113))
- Every money-service change takes a `requestId` in its body and answers a repeat with the first result, or 409 `REQUEST_ID_REUSED` when the same id carries a different change. `/money/decrease` lowers the balance, `/money/decrease-eda` is gone, and balances and amounts are 64-bit in money-service, remittance-service and money-aggregation-service. Wallets live in the new `wallet` table, so wallets in a local database from before start again from zero ([#96](https://github.com/chrysaor/JayPay/issues/96))

### Fixed

- A wallet balance stays correct: money-service refuses an amount that is not a whole number from 1 to `MONEY_AMOUNT_MAX` (10,000,000 by default) with 400 and changes nothing, a decrease never takes a balance below zero, each member has one wallet, and concurrent or repeated requests count exactly once ([#96](https://github.com/chrysaor/JayPay/issues/96))
### Security

- The customer site forwards only the 11 requests its screens make and answers 404 to every other `/api/` path, so the services' internal endpoints, Actuator and the OpenAPI pages no longer answer through it ([#110](https://github.com/chrysaor/JayPay/pull/110))
- The admin site picks the upstream from the normalized path, so a path with an encoded `..` such as `/api/membership/..%2Fauth/staff/me` no longer reaches membership-service without the staff check ([#110](https://github.com/chrysaor/JayPay/pull/110))

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
