# A restart of money-service dropped the deadlines of running top-ups

## What happened

While fixing [#183](https://github.com/chrysaor/JayPay/issues/183), reading the code showed that a restart of money-service drops every pending saga deadline ([#193](https://github.com/chrysaor/JayPay/issues/193)). A top-up waiting on the bank then stays at the status `0` for good, and a rollback waiting for its confirmation is never sent again nor stored as `ROLLBACK_UNCONFIRMED`, so a charge may not be returned and no state shows it. It is in 0.4.0 and on `develop`. No user or operator reported it, and it was not seen on a running stack.

## Cause

`MoneyConfig` builds a `SimpleDeadlineManager`, which keeps each deadline only in the memory of the instance that scheduled it. The saga itself is stored, but nothing else ever acts on a stored saga whose deadline was lost, because the saga waits for an event that will not come.

## Why the checks missed it

- Every saga test ran in one Spring context that never stopped, so the deadlines it scheduled always fired.
- [0025](../decisions/0025-top-up-rollback-sent-again-by-the-saga.md) wrote the gap down as a limit, but no test showed what a lost deadline does.
- `make smoke` restarts nothing in the middle of a top-up.

## Fix

[#193](https://github.com/chrysaor/JayPay/issues/193): the saga keeps the time its deadline is due, and money-service sweeps the running sagas at start and every `money.recharge.sweep.every`. A saga past its due time by more than that period runs the deadline itself. The decision is in [0026](../decisions/0026-lost-top-up-deadlines-found-by-a-sweep.md).

## What changed so it is caught next time

- `RechargeSagaTest.aRechargeWaitingForTheBankWhoseDeadlineIsLostStillFailsAndIsRolledBackOnce` and `aRollbackWhoseRetryIsLostIsStillSentUpToItsAttemptsAndLeavesTheRechargeRollbackUnconfirmed` drop the scheduled deadlines with `DeadlineManager.cancelAll`, the way a restart drops them, and failed before the fix.
- `RechargeSagaTest.aRechargeWhoseDeadlineIsLostIsRolledBackOnceWhileSeveralInstancesSweepAtOnce` sweeps from two threads at once and checks one rollback.
- The first version of that test swept in a tight loop. The flood of events held the rollback confirmation back for more than the rollback wait, so the live rollback deadline sent a second rollback. The log showed the second send came from the deadline, not from a sweep. The test now sweeps every 100 milliseconds, as an instance on a schedule would.
