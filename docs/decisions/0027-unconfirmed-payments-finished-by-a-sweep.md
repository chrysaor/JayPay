# 0027. A sweep finishes payments that money-service did not confirm

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

Since [0017](0017-payment-resumes-before-checking-parties.md), a repeated request always finishes a payment whose debit answer was lost. A caller that never repeats leaves the payment `REQUESTED`, with the price taken from the wallet when the debit landed. 0017 names this as the part it left open ([#171](https://github.com/chrysaor/JayPay/issues/171)).

## Options

The issue names three:

- Leave it to the caller.
- A recovery job in payment-service that asks money-service again under the stored debit id after a delay.
- An operator action in the admin console.

The owner chose the recovery job, a sweep like the one of [0026](0026-lost-top-up-deadlines-found-by-a-sweep.md), with no new library and little upkeep. The owner also asked that the sweep run at start and on a schedule, that it finish a payment through the same code as a repeated request, that it stop after a set number of attempts and leave the payment where an operator sees it, that instances sweeping at once and a request arriving during a sweep never take the price twice, and that the period, the delay and the number of attempts be `payment` settings.

## Decision

`PaymentSweeper` runs at start and every `payment.sweep.every`, 30 seconds by default. One `UPDATE ... RETURNING` picks the `REQUESTED` payments older than `payment.sweep.retry-after` times one more than their `sweep_attempts`, locks them with `FOR UPDATE SKIP LOCKED`, and adds one to `sweep_attempts`. With the defaults, a payment is swept about 1, 2, 3, 4 and 5 minutes after its request, `payment.sweep.attempts` times. `requested_at`, which 0.4.0 already stores, is the time of the request. `sweep_attempts` is a new column with the default 0, so a database that 0.4.0 created takes it on start (`SchemaUpgradeTest.aPaymentStoredByTheLastReleaseHasNoSweepsAndTheSweepRuns`).

Each picked payment goes through `PaymentService.resume`, the code a repeated request runs: it calls money-service under `payment-<paymentId>-decrease` and moves the payment from `REQUESTED` to `PAID` or `FAILED` with one guarded `UPDATE`. When money-service does not answer the last sweep, payment-service logs an error with the payment id and the debit id, and the payment stays `REQUESTED` with `sweep_attempts` at the limit. No new status is added, so settlement-service, which reads only `PAID` payments, sees nothing new, and a repeated request still finishes the payment.

The row lock keeps instances from picking a payment twice in one wait. A sweep and a repeated request that reach the same payment at once both send the same debit id, which money-service applies once, and only the first guarded `UPDATE` moves the status (`PaymentInvariantsTest.sweepsAndRepeatsOfALostPaymentAtOnceDebitItOnce`).

## What we gave up

- A payment money-service did not confirm finishes a minute or more after its request, not at once.
- After the last sweep, an operator finds the payment in the error log or in the `payment` table. The admin console does not show it.
- A `payment.sweep.retry-after` shorter than `jaypay.http.request-timeout`, 10 seconds, lets the sweep send a debit while the first request still waits on it. money-service applies it once.
- The sweep reads every `REQUESTED` payment that is due in one statement, without a limit per run.

## When to decide again

When a payment has to finish sooner after a lost answer, or when an operator has to act on an unconfirmed payment from the admin console.

## Links

- Issue [#171](https://github.com/chrysaor/JayPay/issues/171)
- Decision [0017](0017-payment-resumes-before-checking-parties.md)
- Decision [0026](0026-lost-top-up-deadlines-found-by-a-sweep.md)
