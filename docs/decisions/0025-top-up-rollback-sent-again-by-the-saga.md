# 0025. The saga sends a top-up rollback again, a set number of times

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

When the bank does not confirm the charge of a top-up in time, the saga fails the top-up and sends `RollbackFirmBankingRequestCommand` to banking-service. When that command failed, the saga logged it and nothing sent it again, so a charge that had left the member's bank account was not returned and an operator could not see it ([#183](https://github.com/chrysaor/JayPay/issues/183)). See the [retrospective](../retrospectives/2026-11-27-top-up-rollback-sent-once.md).

## Options

The issue names two senders:

- The saga, on a deadline.
- A scheduled job over top-ups whose rollback is not confirmed.

It also asks how long to retry and what an operator sees when the retries stop. The owner ruled out retrying without end, and chose the saga on a deadline with a wait that grows after each send, a set number of sends, and a mark on the top-up that an operator can find, with the number and the wait in settings under `money.recharge`.

## Decision

Each time the saga sends the rollback, it schedules the deadline `rollback-deadline` and keeps one `rollbackFirmBankingId` for every send, so the confirmation of any send ends the saga. When the deadline fires first, the saga sends the rollback again with a new aggregate id. The first wait is `money.recharge.rollback.retry-after`, 30 seconds by default, and each wait after it is twice the one before. After `money.recharge.rollback.attempts` sends, five by default, the last wait ends without a confirmation, and the saga logs an error, stores the wallet change as `ROLLBACK_UNCONFIRMED`, and ends. A repeat of the same top-up answers `moneyChangingResultStatus` `3`. With the defaults, the saga gives up about 15 minutes after the first send.

banking-service refunds a charge once however many rollbacks arrive, because the refund always uses the request id `recharge-<rechargingRequestId>-refund` (`BankTransferTest.aRollbackReturnsALandedChargeToTheMemberOnce`). A rollback that banking-service handled but whose answer was lost is therefore safe to send again. `EnumValueChecks` from [0022](0022-enum-columns-without-value-checks.md) lets a database from 0.4.0 store the new status (`SchemaUpgradeTest`).

## What we gave up

- The deadlines live in the memory of the money-service instance (`SimpleDeadlineManager`). A restart during the retries drops them, and that top-up stays `FAILED` without the mark, the same way a restart drops the recharge deadline today. [0026](0026-lost-top-up-deadlines-found-by-a-sweep.md) covers this.
- An operator finds an unconfirmed rollback in the `wallet_change` table by its status, or in the error log. The admin console does not show it.
- `RollbackFirmBankingFinishedEvent` does not say whether the bank confirmed the refund. A rollback whose refund the bank has not answered still ends the saga. [0027](0027-top-up-rollback-ends-on-a-confirmed-refund.md) covers this.

## When to decide again

When the deadlines need to survive a restart, which needs a deadline manager that stores them, or when the admin console has to show unconfirmed rollbacks.

## Links

- Issue [#183](https://github.com/chrysaor/JayPay/issues/183)
- PR [#191](https://github.com/chrysaor/JayPay/pull/191)
- Retrospective [A top-up rollback that failed was never sent again](../retrospectives/2026-11-27-top-up-rollback-sent-once.md)
