# A top-up rollback that failed was never sent again

## What happened

The failure path table of [#142](https://github.com/chrysaor/JayPay/issues/142) found it while it filled the cell of `RollbackFirmBankingRequestCommand` ([#181](https://github.com/chrysaor/JayPay/pull/181)). When the bank did not confirm the charge of a saga top-up in time, the saga failed the top-up and sent `RollbackFirmBankingRequestCommand` to banking-service once. When banking-service refused the command or its answer never came, the saga logged it and waited for an event that would not come. The top-up was `FAILED` and the wallet was not credited, but a charge that had left the member's bank account was not returned, and nothing showed an operator that it was not. It was in 0.4.0. No user or operator reported it.

## Cause

`MoneyRechargeSaga.rollBack()` cancelled the recharge deadline before it sent the rollback and did not schedule anything in its place. The `exceptionally` branch of the send only logged. From that moment no deadline, handler or job could act on the saga again unless `RollbackFirmBankingFinishedEvent` arrived.

## Why the checks missed it

- `RechargeSagaTest.aRechargeWhoseRollbackAnswerIsLostStaysFailedAndIsNeitherChargedNorRolledBackAgain` lost the rollback answer and asserted that the rollback was sent once. It stated the missing retry as the expected behaviour.
- The fake banking-service in `RechargeSagaTest` could refuse a charge and a check, but never a rollback, so no test sent a rollback that failed.
- `BankTransferTest.aRollbackReturnsALandedChargeToTheMemberOnce` showed that banking-service refunds once when two rollbacks arrive, but nothing on the money-service side ever sent the second one.
- Until #142, no list named each way a call between services can fail, so nobody asked what happens after a rollback fails.

## Fix

[#191](https://github.com/chrysaor/JayPay/pull/191): the saga schedules a rollback deadline with each send, sends the rollback again when no confirmation arrived, doubles the wait each time, and after `money.recharge.rollback.attempts` sends stores the top-up as `ROLLBACK_UNCONFIRMED` and ends. The decision is in [0024](../decisions/0024-top-up-rollback-sent-again-by-the-saga.md).

## What changed so it is caught next time

- `RechargeSagaTest.aRollbackBankingServiceRefusesIsSentAgainUntilBankingServiceTakesIt` refuses the first rollback and checks that the second one is taken.
- `RechargeSagaTest.aRechargeWhoseRollbackAnswerIsLostIsRolledBackAgainAndIsNotChargedAgain` replaces the test that expected one send. It loses the first answer and checks the second send, one charge and an unchanged wallet.
- `RechargeSagaTest.aRollbackThatIsNeverConfirmedStopsAfterItsAttemptsAndLeavesTheRechargeRollbackUnconfirmed` refuses every rollback and checks the number of sends and the status `3` a repeat answers.
- The cell in [docs/architecture.md](../architecture.md#failure-paths-between-services) names these tests.
- A restart of money-service during the retries still drops them, see the decision record.
