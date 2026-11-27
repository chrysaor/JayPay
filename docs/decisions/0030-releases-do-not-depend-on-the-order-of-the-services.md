# 0030. A release does not depend on the order in which the services are upgraded

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

[0028](0028-top-up-rollback-ends-on-a-confirmed-refund.md) added `refundStatus` to `RollbackFirmBankingFinishedEvent` and asked to upgrade money-service before banking-service ([#199](https://github.com/chrysaor/JayPay/issues/199)). The owner said that an order between deployments makes the rollout complex. The target is Kubernetes with Argo CD, which syncs every workload at once and gives no order.

A money-service of 0.4.0 was run against the messages of a newer banking-service, with its own serializer settings and saga:

- An event of a type it knows with a field it does not know stops the saga processor. The Axon serializer of 0.4.0 refuses the field while the saga manager reads the association value, the tracking processor goes into retry mode and retries the same event without end, and no top-up after it is processed.
- An event of a type it does not have is skipped. The processor moves past it and later top-ups finish.

See the [retrospective](../retrospectives/2026-11-27-upgrade-depended-on-the-order-of-the-services.md).

## Options

The issue names no options. Keeping the order was ruled out by the issue. The services of 0.4.0 are released, so their serializer cannot change, and only the messages of 0.5.0 can.

## Decision

A release works in any order of the services, and a service of the release before keeps working next to it during the rollout.

- `MessageJsonAutoConfiguration` in `libs/common` gives Axon the `defaultAxonObjectMapper` that it uses for the events and messages serializers. It ignores a field it does not know, so every service from 0.5.0 on reads an event or command that a later release added a field to.
- A field may be added to an event or command when a reader of the release before still does the right thing without it. A new meaning that it must not miss goes in a new event type, which the release before skips.
- The three answers to a top-up rollback are three event types. `RollbackFirmBankingFinishedEvent` has the fields of 0.4.0 again and means that the refund is confirmed. `RollbackFirmBankingUnconfirmedEvent` and `RollbackFirmBankingRefusedEvent` are new.

`StoredRechargeMessagesTest` checks that a confirmed refund is written with exactly the fields that 0.4.0 reads and that the event of 0.4.0 is read as a confirmed refund. `RechargeSagaTest.readsAnEventAndACommandWithAFieldThisReleaseDoesNotKnow` reads an event and a command with an unknown field through the serializers of the running money-service.

## What we gave up

- During the rollout, a refund that a banking-service of 0.4.0 did not confirm reads as confirmed, and a money-service of 0.4.0 skips the unconfirmed and refused answers, so its saga keeps waiting. 0028 lists both.
- A message class keeps its name and the fields that a reader of the release before needs. Removing one needs a release in which nothing needs it.
- The saga state and the tracking tokens are stored with XStream, which this rule does not cover.
- While money-service still runs 0.4.0, it publishes no `WalletRechargedEvent`, so a money-query-service of 0.5.0 leaves those top-ups out of the region total for good.
- The rule covers Axon messages, and no test checks the HTTP calls between the services against the release before. The code of 0.4.0 shows one call that breaks it: `POST /banking/firmbanking/request` of 0.4.0 answers 400 to a request without `fromBankName` and `fromBankAccountNumber`, which remittance-service and settlement-service of 0.5.0 no longer send, and both take 400 as a final refusal. This record does not fix it.

## When to decide again

When the services move to a schema registry or another serializer, or when a rollout gets an order, such as an Argo CD sync wave.

## Links

- Issue [#199](https://github.com/chrysaor/JayPay/issues/199), [#194](https://github.com/chrysaor/JayPay/issues/194)
- [0028](0028-top-up-rollback-ends-on-a-confirmed-refund.md)
- [Retrospective](../retrospectives/2026-11-27-upgrade-depended-on-the-order-of-the-services.md)
- [CONTRIBUTING.md](../../CONTRIBUTING.md#messages-between-releases)
