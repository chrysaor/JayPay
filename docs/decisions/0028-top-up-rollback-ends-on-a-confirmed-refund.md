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

`RollbackFirmBankingFinishedEvent` carries `refundStatus`:

| Value | Name | When |
| --- | --- | --- |
| `0` | `UNCONFIRMED` | The bank did not answer the refund, or still does not answer the charge |
| `1` | `CONFIRMED` | The refund landed, or no charge landed, so nothing is owed back |
| `2` | `REFUSED` | The bank refused the refund |

The saga ends on `CONFIRMED`. On `UNCONFIRMED` it keeps its rollback deadline, so the rollback is sent again, and banking-service asks the bank again under the same refund id `recharge-<rechargingRequestId>-refund`, which lands the refund once (`BankTransferTest.aRollbackWhoseRefundTheBankDoesNotAnswerFinishesUnconfirmedAndTheNextRollbackAsksTheBankAgain`). After the attempts of 0025 the top-up is stored as `ROLLBACK_UNCONFIRMED`.

On `REFUSED` the saga stores `ROLLBACK_UNCONFIRMED` at once and ends, without waiting for the attempts. A refund the bank refused stays refused under its request id, and banking-service answers it again without asking the bank (`BankTransferTest.aRollbackWhoseRefundTheBankRefusesFinishesRefusedWithoutAskingTheBankAgain`), so sending the rollback again cannot return the money. banking-service and money-service log it as an error, because an operator has to return the money.

A rollback whose charge the bank still does not answer was reported as if no charge had landed. It is now `UNCONFIRMED`, because the charge can still land.

An event stored before this change has no `refundStatus` and reads as `0`, `UNCONFIRMED`. The saga then sends the rollback again, and the new banking-service answers with the real status. Reading it as confirmed would end the saga the way this issue describes.

## What we gave up

- A refund the bank refused has no retry and no path in JayPay to return the money. An operator finds it as `ROLLBACK_UNCONFIRMED` in the `wallet_change` table or in the error log.
- The Axon event serializer refuses a field it does not know, so a money-service from before this change cannot read the new event. Deploy money-service before banking-service. While banking-service still runs the version before, every rollback answer reads as `UNCONFIRMED`, and the saga sends the rollback again. Once banking-service runs this version, the next send gets the real status. A top-up whose attempts all end before that is stored as `ROLLBACK_UNCONFIRMED` even when the refund landed.
- The refund status is a number, like the status of `RequestFirmBankingFinishedEvent`, so a value added later reads as unconfirmed by an older saga.

## When to decide again

When a refused refund needs a way back to the member inside JayPay, or when the admin console has to show unconfirmed rollbacks.

## Links

- Issue [#194](https://github.com/chrysaor/JayPay/issues/194)
- Decision [0025](0025-top-up-rollback-sent-again-by-the-saga.md)
- Retrospective [A top-up rollback ended while the bank had not confirmed the refund](../retrospectives/2026-11-27-top-up-rollback-ended-on-an-unconfirmed-refund.md)
