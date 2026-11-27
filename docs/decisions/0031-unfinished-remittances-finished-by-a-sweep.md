# 0031. A sweep finishes remittances that were not confirmed

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

A remittance whose debit, credit, bank transfer or refund was not confirmed finishes when the same request arrives again. A caller that never repeats leaves it `REQUESTED`, `DEBITED`, `TRANSFERRING` or `REFUNDING`, and the sender's money can stay taken. [0029](0029-refund-passes-whatever-the-member-state.md) names this as the part it left open. Payments ([0027](0027-unconfirmed-payments-finished-by-a-sweep.md)) and saga top-ups ([0026](0026-lost-top-up-deadlines-found-by-a-sweep.md)) are finished by a sweep, remittances were not ([#201](https://github.com/chrysaor/JayPay/issues/201)).

It also left an upgrade order: a refund sent to a money-service from before [#184](https://github.com/chrysaor/JayPay/issues/184), which has no `POST /money/refund`, is not confirmed, and without a repeat it is never sent again. The changelog asked to upgrade money-service before remittance-service.

## Options

The issue names one: remittance-service finishes an unfinished remittance by itself, through the same path a repeated request takes, with a limit on the attempts and an error when it gives up, and with sweeps and repeated requests at once moving the money once. The owner put it into 0.5.0 so that the release does not depend on the order of the services.

## Decision

The sweep has the shape of the payment sweep of 0027, with the same defaults. `RemittanceSweeper` runs at start and every `remittance.sweep.every`, 30 seconds. One `UPDATE ... RETURNING` picks the remittances that are not `COMPLETED`, `FAILED` or `REFUNDED` and are older than `remittance.sweep.retry-after`, 60 seconds, times one more than their `sweep_attempts`, locks them with `FOR UPDATE SKIP LOCKED`, and adds one to `sweep_attempts`. A remittance is swept about 1, 2, 3, 4 and 5 minutes after its request, `remittance.sweep.attempts` times. The count is for the remittance, not for each status. `requested_at`, which 0.4.0 already stores, is the time of the request. `sweep_attempts` is a new column with the default 0, so a database that 0.4.0 created takes it on start (`SchemaUpgradeTest.aRemittanceStoredByTheLastReleaseHasNoSweepsAndTheSweepRuns`). The list of unfinished statuses comes from `RemittanceStatus`, so the query and the code agree on what is final.

Each picked remittance goes through `RemittanceService.drive`, the code a repeated request runs. It carries the remittance on from its status with the request ids `remittance-<remittanceId>-decrease`, `-increase`, `-transfer` and `-refund`, and moves the status with one guarded `UPDATE` per step. A step that is not confirmed stops the drive where it is: a repeated request answers 503 with `REMITTANCE_NOT_CONFIRMED`, and the sweep leaves the remittance for its next sweep. When the last sweep gets no confirmation, remittance-service logs an error with the remittance id and the status it stays in, and the remittance keeps that status with `sweep_attempts` at the limit. No new status is added, and a repeated request still finishes the remittance.

The row lock keeps instances from picking a remittance twice in one wait. A sweep and a repeated request that reach the same remittance at once send the same request ids. money-service applies each once, banking-service stores a transfer once per request id, and only the first guarded `UPDATE` moves the status (`RemittanceInvariantsTest.sweepsAndRepeatsOfALostDebitAtOnceMoveTheMoneyOnce`, `sweepsAndRepeatsOfAnUnconfirmedTransferAtOnceLandItOnce`). A transfer the bank did not confirm is asked again under the same transfer id and lands once (`aTransferTheBankDidNotConfirmIsCompletedByTheSweepUnderTheSameTransferIdAndLandsOnce`).

A refund that a money-service from before #184 does not know is not confirmed, and the sweep sends it again until money-service is upgraded (`aRefundThatAMoneyServiceWithoutTheRefundEndpointDidNotTakeIsRefundedByTheSweepAfterTheUpgrade`). The changelog no longer asks for an order between the two services.

## What we gave up

- A remittance that was not confirmed finishes a minute or more after its request, not at once.
- After the last sweep, an operator finds the remittance in the error log or in the `remittance` table. The admin console does not show it.
- A money-service that stays older than #184 for longer than the five sweeps of a remittance leaves that remittance `REFUNDING` at the error log, waiting for a repeat. A remittance left unfinished before the upgrade has waited longer than every sweep, so its five sweeps come within about two minutes of the start of remittance-service.
- A refund that money-service refuses with `NOT_REFUNDABLE` is swept like an unanswered one, five times, before the error.
- A `remittance.sweep.retry-after` shorter than `jaypay.http.request-timeout`, 10 seconds, lets the sweep send a call while the first request still waits on it. money-service and banking-service apply it once.
- The sweep reads every unfinished remittance that is due in one statement, without a limit per run.

## When to decide again

When a remittance has to finish sooner after a lost answer, when an operator has to act on an unfinished remittance from the admin console, or when a refund refused as `NOT_REFUNDABLE` needs its own handling.

## Links

- Issue [#201](https://github.com/chrysaor/JayPay/issues/201)
- Decision [0026](0026-lost-top-up-deadlines-found-by-a-sweep.md)
- Decision [0027](0027-unconfirmed-payments-finished-by-a-sweep.md)
- Decision [0029](0029-refund-passes-whatever-the-member-state.md)
