# A top-up rollback ended while the bank had not confirmed the refund

## What happened

While fixing [#183](https://github.com/chrysaor/JayPay/issues/183) in [#191](https://github.com/chrysaor/JayPay/pull/191), reading the code showed that a top-up rollback counted as finished when the bank had not answered the refund ([#194](https://github.com/chrysaor/JayPay/issues/194)). banking-service published `RollbackFirmBankingFinishedEvent`, the saga ended, and nothing asked the bank about the refund again. The top-up stayed `FAILED` with no mark an operator could find, while the member's money might still be in the company account. The same happened when the bank still did not answer the charge at the time of the rollback, and when the bank refused the refund. It was on `develop` after #191. No user or operator reported it.

## Cause

`FirmBankingRequestAggregate` read the refund status only for its log line and the event flow step, and published the same `RollbackFirmBankingFinishedEvent` for every status. The event had no field for it, and `MoneyRechargeSaga` ended on the event with `@EndSaga`. `TransferMoneyService.reverse` also answered "no refund needed" for a charge the bank had not answered, the same as for a charge that never landed.

## Why the checks missed it

- The fake banking-service in `RechargeSagaTest` could refuse a rollback or lose its answer, but always confirmed the refund when it answered, because the event had nothing else to say.
- `BankTransferTest` covered a refund that landed once and a charge settled at the rollback, but never a refund the bank did not answer or refused, and never read the event the rollback published.
- 0025 wrote the gap down as a limit, which kept it from blocking #191 but did not test it.

## Fix

[#194](https://github.com/chrysaor/JayPay/issues/194): `RollbackFirmBankingFinishedEvent` carries `refundStatus`. The saga ends only on a confirmed refund, sends the rollback again on an unconfirmed one up to its attempts, and stores `ROLLBACK_UNCONFIRMED` at once on a refused one. `reverse` reports a charge the bank still does not answer as unconfirmed. The decision is in [0028](../decisions/0028-top-up-rollback-ends-on-a-confirmed-refund.md).

## What changed so it is caught next time

- The fake banking-service in `RechargeSagaTest` can leave a refund unconfirmed or refuse it. `aRollbackWhoseRefundTheBankHasNotConfirmedIsSentAgainUntilTheRefundIsConfirmed`, `aRollbackWhoseRefundIsNeverConfirmedStopsAfterItsAttemptsAndLeavesTheRechargeRollbackUnconfirmed` and `aRollbackWhoseRefundTheBankRefusesLeavesTheRechargeRollbackUnconfirmedWithoutSendingItAgain` failed before the fix.
- `BankTransferTest` reads the refund status of each rollback for a refund the bank does not answer, refuses, a charge it does not answer, and a charge it refused.
- `StoredRechargeMessagesTest.readsARollbackFinishedBeforeTheRefundStatusExistedAsUnconfirmed` keeps an event stored before the field from reading as confirmed.
- The failure path table in [docs/architecture.md](../architecture.md#failure-paths-between-services) has a row for the bank's answer to the refund.
