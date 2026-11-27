# 0028. A top-up rollback ends only on a refund the bank confirmed

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

banking-service published `RollbackFirmBankingFinishedEvent` for every rollback it handled, also when the bank had not answered the refund. The event did not say whether the bank confirmed the refund, and the top-up saga ended on it, so it stopped sending the rollback while the money might not be back ([#194](https://github.com/chrysaor/JayPay/issues/194)). [0025](0025-top-up-rollback-sent-again-by-the-saga.md) listed this as a limit. See the [retrospective](../retrospectives/2026-11-27-top-up-rollback-ended-on-an-unconfirmed-refund.md).

## Options

The issue names two:

- The event carries the refund status, and the saga keeps sending the rollback while the refund is pending.
- banking-service settles its own pending refunds and tells nobody.

The owner chose the first, with the retry limit of 0025 and no retrying without end.

## Decision

banking-service answers each rollback with one of three events. [0030](0030-releases-do-not-depend-on-the-order-of-the-services.md) made them three types in place of one event with a status field, so that a money-service of 0.4.0 reads the confirmed one and skips the others:

| Event | When |
| --- | --- |
| `RollbackFirmBankingUnconfirmedEvent` | The bank did not answer the refund, or still does not answer the charge |
| `RollbackFirmBankingFinishedEvent` | The refund landed, or no charge landed, so nothing is owed back |
| `RollbackFirmBankingRefusedEvent` | The bank refused the refund |

The saga ends on `RollbackFirmBankingFinishedEvent`. On `RollbackFirmBankingUnconfirmedEvent` it keeps its rollback deadline, so the rollback is sent again, and banking-service asks the bank again under the same refund id `recharge-<rechargingRequestId>-refund`, which lands the refund once (`BankTransferTest.aRollbackWhoseRefundTheBankDoesNotAnswerFinishesUnconfirmedAndTheNextRollbackAsksTheBankAgain`). After the attempts of 0025 the top-up is stored as `ROLLBACK_UNCONFIRMED`.

On `RollbackFirmBankingRefusedEvent` the saga stores `ROLLBACK_UNCONFIRMED` at once and ends, without waiting for the attempts. A refund the bank refused stays refused under its request id, and banking-service answers it again without asking the bank (`BankTransferTest.aRollbackWhoseRefundTheBankRefusesFinishesRefusedWithoutAskingTheBankAgain`), so sending the rollback again cannot return the money. banking-service and money-service log it as an error, because an operator has to return the money.

A rollback whose charge the bank still does not answer was reported as if no charge had landed. It is now `RollbackFirmBankingUnconfirmedEvent`, because the charge can still land.

A `RollbackFirmBankingFinishedEvent` of a banking-service of 0.4.0 reads as a confirmed refund, because it has the same type and fields. That banking-service sent it for every rollback, so during an upgrade a refund it did not confirm ends the saga the way this issue describes.

## What we gave up

- A refund the bank refused has no retry and no path in JayPay to return the money. An operator finds it as `ROLLBACK_UNCONFIRMED` in the `wallet_change` table or in the error log.
- While banking-service still runs 0.4.0, a refund that the bank did not answer or refused ends the saga as confirmed, and the top-up stays `FAILED`. While money-service still runs 0.4.0, it skips the unconfirmed and the refused answers of a new banking-service, and its saga keeps waiting with no deadline. A money-service of 0.5.0 does not send that rollback again either, because the saga of 0.4.0 kept no rollback deadline. Both cases last only for the rollout.

## When to decide again

When a refused refund needs a way back to the member inside JayPay, or when the admin console has to show unconfirmed rollbacks.

## Links

- Issue [#194](https://github.com/chrysaor/JayPay/issues/194)
- Decision [0025](0025-top-up-rollback-sent-again-by-the-saga.md)
- Retrospective [A top-up rollback ended while the bank had not confirmed the refund](../retrospectives/2026-11-27-top-up-rollback-ended-on-an-unconfirmed-refund.md)
- Issue [#199](https://github.com/chrysaor/JayPay/issues/199) and decision [0030](0030-releases-do-not-depend-on-the-order-of-the-services.md), which made the three answers three event types
