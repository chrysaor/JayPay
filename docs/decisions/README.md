# Decisions

One file for each decision that shaped JayPay: the situation, the options, the choice, what it cost, and the release it was made for. The code shows what was built. These records say why, so that someone who takes the project over does not have to dig through issues and PRs.

## When to write one

Add a record in the PR that makes the decision when any of these is true:

- The PR picks one of two or more options that an issue or a review named.
- It adds a component, a workload, a Kafka topic or a tool, or removes one.
- It changes a rule in `CLAUDE.md`, `CONTRIBUTING.md` or the gate.
- It undoes or replaces an earlier decision. Mark the old record `superseded` and link the new one.

A bug fix that follows the existing design does not need one. Write down only what the issue, the PR or the review says. When the reason for a choice is not written anywhere, ask before you record it.

## Format

Name the file `NNNN-short-name.md` with the next free number, and add it to the index below. Use these sections, in this order:

| Section | What it says |
| --- | --- |
| Release | The release the decision went into, as `X.Y.Z`. A decision on `develop` that is not released yet is `X.Y.Z (unreleased)` with the next milestone |
| Status | `accepted`, `superseded by NNNN`, or `open` |
| Context | The situation and the problem, with the evidence |
| Options | The options that were on the table. Say so when only one was recorded |
| Decision | What was chosen and how it works now |
| What we gave up | The costs, limits and leftovers the choice accepted |
| When to decide again | The event or the open issue that would reopen it |
| Links | The issues and PRs |

Keep it to one page. Leave out secrets, account values and request examples that would work as an attack.

## Index

| # | Decision | Release | Status | Evidence |
| --- | --- | --- | --- | --- |
| [0001](0001-java-25-and-spring-boot-4.md) | Rebuild the backend on Java 25, Spring Boot 4 and PostgreSQL | 0.1.0 | accepted | [#44](https://github.com/chrysaor/JayPay/pull/44), [changelog 0.1.0](../../CHANGELOG.md#010---2026-11-25) |
| [0002](0002-hexagonal-layers-checked-by-archunit.md) | Hexagonal layers in every service, checked by ArchUnit | 0.1.0 | accepted | [#57](https://github.com/chrysaor/JayPay/pull/57), [changelog 0.1.0](../../CHANGELOG.md#010---2026-11-25) |
| [0003](0003-one-gate-for-local-runs-and-ci.md) | One gate, `make verify`, for local runs and CI | 0.1.0 | accepted | [#48](https://github.com/chrysaor/JayPay/pull/48), [changelog 0.1.0](../../CHANGELOG.md#010---2026-11-25) |
| [0004](0004-branch-flow-and-owner-confirmed-releases.md) | `master`, `develop` and release branches, with releases confirmed by the owner | 0.1.0 | accepted | [#48](https://github.com/chrysaor/JayPay/pull/48), [changelog 0.1.0](../../CHANGELOG.md#010---2026-11-25) |
| [0005](0005-tools-and-images-pinned-by-digest.md) | Scanners, tools and base images pinned by digest | 0.1.0, 0.3.0 | accepted | [#45](https://github.com/chrysaor/JayPay/pull/45), [#105](https://github.com/chrysaor/JayPay/pull/105), [changelog 0.1.0](../../CHANGELOG.md#010---2026-11-25), [changelog 0.3.0](../../CHANGELOG.md#030---2026-11-26) |
| [0006](0006-staff-sign-in-in-auth-service.md) | Staff sign-in written in a new auth-service | 0.2.0 | superseded by 0010 | [#70](https://github.com/chrysaor/JayPay/pull/70), [changelog 0.2.0](../../CHANGELOG.md#020---2026-11-25) |
| [0007](0007-kubernetes-and-gitops-foundation.md) | Kubernetes and GitOps as the target, with only the foundation in this repository | 0.3.0 | accepted | [#78](https://github.com/chrysaor/JayPay/pull/78), [#80](https://github.com/chrysaor/JayPay/pull/80), [#82](https://github.com/chrysaor/JayPay/pull/82), [#87](https://github.com/chrysaor/JayPay/pull/87), [changelog 0.3.0](../../CHANGELOG.md#030---2026-11-26) |
| [0008](0008-dependabot-merges-patch-and-minor.md) | Dependabot patch and minor updates merge by themselves | 0.3.0 | accepted | [#92](https://github.com/chrysaor/JayPay/pull/92), [changelog 0.3.0](../../CHANGELOG.md#030---2026-11-26) |
| [0009](0009-changelog-checked-by-the-gate.md) | A changelog that the gate keeps current and the release publishes | 0.4.0 | accepted | [#116](https://github.com/chrysaor/JayPay/pull/116), [changelog 0.4.0](../../CHANGELOG.md#040---2026-11-26) |
| [0010](0010-staff-sign-in-through-keycloak.md) | Staff sign in through Keycloak | 0.4.0 | accepted | [#120](https://github.com/chrysaor/JayPay/pull/120), [changelog 0.4.0](../../CHANGELOG.md#040---2026-11-26) |
| [0011](0011-kafka-for-events-not-tasks.md) | Kafka stays for events, and the Kafka task top-up goes | 0.4.0 | accepted | [#128](https://github.com/chrysaor/JayPay/pull/128), [changelog 0.4.0](../../CHANGELOG.md#040---2026-11-26) |
| [0012](0012-event-flow-service-and-topic.md) | Event flow as its own service fed by a Kafka topic | 0.4.0 | accepted | [#131](https://github.com/chrysaor/JayPay/pull/131), [#145](https://github.com/chrysaor/JayPay/pull/145), [changelog 0.4.0](../../CHANGELOG.md#040---2026-11-26) |
| [0013](0013-saga-first-token-at-stream-start.md) | The saga processor's first token starts at the beginning of the stream | 0.4.0 | accepted | [#133](https://github.com/chrysaor/JayPay/pull/133), [changelog 0.4.0](../../CHANGELOG.md#040---2026-11-26) |
| [0014](0014-flow-type-and-step-stored-as-text.md) | Event flow stores its flow type and step as text | 0.4.0 | accepted | [#145](https://github.com/chrysaor/JayPay/pull/145), [retrospective](../retrospectives/2026-11-26-event-flow-schema-refuses-new-steps.md) |
| [0015](0015-coverage-without-an-external-service.md) | Test coverage measured by JaCoCo and vitest, without an external service | 0.4.0 | accepted | [#141](https://github.com/chrysaor/JayPay/pull/141), [changelog 0.4.0](../../CHANGELOG.md#040---2026-11-26) |
| [0016](0016-java-25-and-node-26-lts.md) | Java stays on 25 and the sites build on Node 26, the LTS lines | 0.5.0 (unreleased) | accepted | [#169](https://github.com/chrysaor/JayPay/pull/169), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0017](0017-payment-resumes-before-checking-parties.md) | A repeated payment resumes before the parties are checked | 0.5.0 (unreleased) | accepted | [#170](https://github.com/chrysaor/JayPay/pull/170), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0018](0018-membership-outage-is-not-a-missing-member.md) | A membership-service outage is not a missing member | 0.5.0 (unreleased) | accepted | [#175](https://github.com/chrysaor/JayPay/pull/175), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0019](0019-payout-attempts-in-settlement-service.md) | settlement-service keeps a payout attempt for each payment | 0.5.0 (unreleased) | accepted | [#174](https://github.com/chrysaor/JayPay/pull/174), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0020](0020-company-account-owned-by-banking-service.md) | banking-service owns the company bank account | 0.5.0 (unreleased) | accepted | [#177](https://github.com/chrysaor/JayPay/pull/177), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0021](0021-region-total-counts-wallet-increases.md) | The region total counts wallet increases, once per recharge | 0.5.0 (unreleased) | accepted | [#178](https://github.com/chrysaor/JayPay/pull/178), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0022](0022-enum-columns-without-value-checks.md) | Enum columns keep no check of their values, without a migration tool | 0.5.0 (unreleased) | accepted | [#180](https://github.com/chrysaor/JayPay/pull/180), [retrospective](../retrospectives/2026-11-27-status-columns-refuse-later-values.md), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0023](0023-kafka-rebalance-latency-total-left-out.md) | The Kafka rebalance latency total is left out of the metrics | 0.5.0 (unreleased) | accepted | [#179](https://github.com/chrysaor/JayPay/pull/179), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0024](0024-log-levels-by-what-the-operator-does.md) | Log levels follow what the operator has to do | 0.5.0 (unreleased) | accepted | [#189](https://github.com/chrysaor/JayPay/issues/189), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0025](0025-top-up-rollback-sent-again-by-the-saga.md) | The saga sends a top-up rollback again, a set number of times | 0.5.0 (unreleased) | accepted | [#191](https://github.com/chrysaor/JayPay/pull/191), [retrospective](../retrospectives/2026-11-27-top-up-rollback-sent-once.md), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0026](0026-lost-top-up-deadlines-found-by-a-sweep.md) | A sweep finds top-up sagas whose deadline a restart dropped | 0.5.0 (unreleased) | accepted | [#193](https://github.com/chrysaor/JayPay/issues/193), [retrospective](../retrospectives/2026-11-27-saga-deadlines-lost-on-restart.md), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0027](0027-unconfirmed-payments-finished-by-a-sweep.md) | A sweep finishes payments that money-service did not confirm | 0.5.0 (unreleased) | accepted | [#171](https://github.com/chrysaor/JayPay/issues/171), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0028](0028-top-up-rollback-ends-on-a-confirmed-refund.md) | A top-up rollback ends only on a refund the bank confirmed | 0.5.0 (unreleased) | accepted | [#194](https://github.com/chrysaor/JayPay/issues/194), [retrospective](../retrospectives/2026-11-27-top-up-rollback-ended-on-an-unconfirmed-refund.md), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0029](0029-refund-passes-whatever-the-member-state.md) | money-service gives back money it took, whatever the state of the member | 0.5.0 (unreleased) | accepted | [#184](https://github.com/chrysaor/JayPay/issues/184), [retrospective](../retrospectives/2026-11-27-refund-refused-for-an-invalid-sender.md), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0030](0030-releases-do-not-depend-on-the-order-of-the-services.md) | A release does not depend on the order in which the services are upgraded | 0.5.0 (unreleased) | accepted | [#199](https://github.com/chrysaor/JayPay/issues/199), [retrospective](../retrospectives/2026-11-27-upgrade-depended-on-the-order-of-the-services.md), [Unreleased](../../CHANGELOG.md#unreleased) |
| [0031](0031-unfinished-remittances-finished-by-a-sweep.md) | A sweep finishes remittances that were not confirmed | 0.5.0 (unreleased) | accepted | [#201](https://github.com/chrysaor/JayPay/issues/201), [Unreleased](../../CHANGELOG.md#unreleased) |

The release of each record comes from the changelog section that lists its PR, or from where the PR's merge falls between the release tags.

## Open decisions

The open issues with the `needs-decision` label. Each waits for the owner. A record is added when the decision is made.

| Decision | Options in the issue | Blocks release | Issue |
| --- | --- | --- | --- |
| Are the service boundaries right for a payment service? | A: keep the eleven services and add merchant data, a ledger and a settlement record. B: A plus merging settlement into payment-service, the two reporting services into one, and removing logging-consumer. C: B plus a merchant service. The review recommends B | not scheduled | [#135](https://github.com/chrysaor/JayPay/issues/135) |
| Which API gateway goes in front of the services | Spring Cloud Gateway, Kubernetes Gateway API with Envoy Gateway, Apache APISIX or Kong in DB-less mode, or nginx as it is. Also: rate limits with Redis or in memory, and Ingress or the Gateway API in the cluster | not scheduled | [#115](https://github.com/chrysaor/JayPay/issues/115) |
| Whether the third-party images join the image scan | Scan them or not, fail on a HIGH finding with no fixed version or not, and accept the Keycloak findings until a fixed image is published or not | not scheduled | [#121](https://github.com/chrysaor/JayPay/issues/121) |
| Supply chain of the release workflow | Splitting verify from publish, Gradle checksums and the otel-collector image need no new component. Signing images does, and waits for the owner's approval | not scheduled | [#102](https://github.com/chrysaor/JayPay/issues/102) |
| Trust between services | The issue lists the gaps (service identity for internal endpoints, one database role per service, Kafka and Axon Server authentication, egress policy) and no options yet | not scheduled | [#100](https://github.com/chrysaor/JayPay/issues/100) |
| Verified apply of the DynamoDB module to AWS | The issue waits for the AWS account profile to use | not scheduled | [#62](https://github.com/chrysaor/JayPay/issues/62) |
