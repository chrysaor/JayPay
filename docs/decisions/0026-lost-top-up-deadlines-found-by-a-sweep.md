# 0026. A sweep finds top-up sagas whose deadline a restart dropped

## Release

0.5.0

## Status

accepted

## Context

money-service keeps the deadlines of the top-up saga in memory (`SimpleDeadlineManager`). A restart drops every pending one: a top-up waiting on the bank stays at the status `0` with its saga open, and a rollback waiting for its confirmation ([0025](0025-top-up-rollback-sent-again-by-the-saga.md)) is not sent again and never reaches `ROLLBACK_UNCONFIRMED` ([#193](https://github.com/chrysaor/JayPay/issues/193)). The owner sees the problem in the recovery after every instance stopped, even with several instances and blue-green deployments.

## Options

The issue names two:

- A deadline manager that stores its deadlines, through Quartz, JobRunr or db-scheduler. It is a new library.
- No new library: at start and on a schedule, money-service looks for top-ups past their time and lets the saga finish them.

The owner chose the second, as long as it takes little upkeep. A library that stores deadlines was not taken, because Axon Framework 5 has no replacement for deadlines yet. The owner also asked that the saga stay the one place that decides, that instances sweeping at once settle a top-up once, that the retry limit of 0025 hold across a restart, and that the sweep period be a `money.recharge` setting.

## Decision

The saga stores the time its current deadline is due next to its other fields, in the saga store. `RechargeSweeper` runs at start and every `money.recharge.sweep.every`, 30 seconds by default. It reads the running top-up sagas from Axon's `association_value_entry` table and publishes `RechargeSweptEvent` with the `rechargingRequestId` of each. A saga whose deadline is due by more than one sweep period cancels the in-memory deadline and runs the same code as the deadline handler, `recharge-deadline` or `rollback-deadline`. Any other saga ignores the event, and an ended saga never receives it.

No row lock is needed. The saga event processor has one segment and one thread, so all events of one saga, from every instance's sweep, are handled one after another, and each sees the state the one before saved. The count of rollback sends lives in the saga store, so the limit holds across a restart (`RechargeSagaTest.aRollbackWhoseRetryIsLostIsStillSentUpToItsAttemptsAndLeavesTheRechargeRollbackUnconfirmed`, `aRechargeWhoseDeadlineIsLostIsRolledBackOnceWhileSeveralInstancesSweepAtOnce`). A live deadline fires a full sweep period before the sweep acts on it.

## What we gave up

- A lost deadline runs between one and two sweep periods after it was due, about a minute late with the defaults.
- A crash after a rollback was sent and before the saga was saved loses that send from the count, so one more rollback can go out. banking-service refunds once.
- A saga that 0.4.0 started has no due time, so the first sweep after the upgrade runs its deadline at once, even before its 60 seconds.
- A live deadline that is late by more than a sweep period on another running instance can run next to the sweep. The wallet change takes a row lock, so the top-up fails once, and banking-service refunds once even if two rollbacks are sent.
- The sweeper reads a table of Axon's JPA saga store and publishes one event for each running saga every period.

## When to decide again

When the saga moves to Axon Framework 5, or when a top-up has to settle closer to its deadline after a restart.

## Links

- Issue [#193](https://github.com/chrysaor/JayPay/issues/193)
- Decision [0025](0025-top-up-rollback-sent-again-by-the-saga.md)
- Retrospective [A restart of money-service dropped the deadlines of running top-ups](../retrospectives/2026-11-27-saga-deadlines-lost-on-restart.md)
