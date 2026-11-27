# The upgrade to 0.5.0 depended on the order of the services

## What happened

[#197](https://github.com/chrysaor/JayPay/pull/197) added `refundStatus` to `RollbackFirmBankingFinishedEvent` and wrote in the changelog that money-service has to be upgraded before banking-service. That order reached `develop`. The owner opened [#199](https://github.com/chrysaor/JayPay/issues/199), because Argo CD syncs every workload at once. Running a money-service of 0.4.0 against the new event showed that the order was not a detail: the saga processor of 0.4.0 refused the event, went into retry mode on it without end, and processed no top-up after it.

## Cause

The Axon serializer of every service used the default `ObjectMapper`, which refuses a field it does not know. #197 added a field to an event that a released service reads, so a released reader could not read it. The saga manager reads the payload to find the saga before any handler runs, so the failure stopped the processor instead of skipping one event.

## Why the checks missed it

- Every test runs the services of one commit together, so no test read a message with a reader of the release before.
- `StoredRechargeMessagesTest` checked that a new service reads a message of 0.4.0, which is only one of the two directions.
- The rollout had no rule about messages, so the order was written down as a limit and passed review.

## Fix

[#199](https://github.com/chrysaor/JayPay/issues/199): every service ignores a field it does not know in an event or command, and the three answers to a rollback are three event types, with `RollbackFirmBankingFinishedEvent` back to its fields of 0.4.0. The decision is in [0030](../decisions/0030-releases-do-not-depend-on-the-order-of-the-services.md).

## What changed so it is caught next time

- `StoredRechargeMessagesTest.writesAConfirmedRefundAsTheEventThatMoneyService040Reads` fails when the event a 0.4.0 money-service reads gets another field.
- `RechargeSagaTest.readsAnEventAndACommandWithAFieldThisReleaseDoesNotKnow` failed before the fix with `UnrecognizedPropertyException`.
- [CONTRIBUTING.md](../../CONTRIBUTING.md#messages-between-releases) says when a field may be added and when a new event type is needed.
