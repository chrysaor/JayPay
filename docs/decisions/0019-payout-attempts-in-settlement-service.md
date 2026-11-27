# 0019. settlement-service keeps a payout attempt for each payment

## Release

0.5.0

## Status

accepted

## Context

settlement-service sent each payout under the fixed request id `settlement-<paymentId>` with the merchant's account as it was at that run. When the bank refused the payout and the merchant registered another account, the next run sent the same id with the new account. banking-service sends one transfer per request id and answers another transfer under a used id with 409 `REQUEST_ID_REUSED`, so settlement-service saw an unconfirmed payout on every run and the payment stayed `PAID` for good ([#138](https://github.com/chrysaor/JayPay/issues/138)). The issue asks for an id per payout attempt, so a refused attempt can be followed by a new one, and places it with the settlement record that the review in [#135](https://github.com/chrysaor/JayPay/issues/135) found missing.

## Options

- Store each payment's current attempt, with its number and account, in settlement-service, and start the next attempt only after the bank refused the current one. The issue recommends this.
- Derive the request id from the payment and the account, with nothing stored. Rejected in this PR: a payout that was not answered, or that landed while its settlement answer was lost, would be sent again to the new account under a new id, and the payment would be paid twice.
- Let banking-service take a new transfer under a request id whose transfer failed. Rejected in this PR: every caller of banking-service relies on one id meaning one transfer, and that rule is what stops a repeat from paying twice.

## Decision

settlement-service has the table `payout_attempt`, one row per payment with the attempt number and the account it is sent to. The first run stores attempt 1 with the merchant's account of that moment. Every run sends the stored attempt with the stored account, under `settlement-<paymentId>` for attempt 1 and `settlement-<paymentId>-<attempt>` after it, so payouts sent before this change keep their id. Only when banking-service answers that the stored attempt failed and the merchant's account differs from the stored one does the run store the next attempt with the new account and send it. The step from one attempt to the next is a conditional update on the attempt number, so concurrent runs agree on one attempt and send the same transfer under the same id. The flow id in `jaypay.flow.events` stays `settlement-<paymentId>`.

## What we gave up

- A refusal with an unchanged account is never sent again. A refusal for a reason that passes needs the merchant to register another account or an operator.
- An unanswered payout stays bound to the account it was first sent to until the bank answers, even when the merchant changes the account.
- settlement-service now stores merchant account numbers, next to the copy in banking-service.
- If `payout_attempt` is lost, attempt numbers start again from 1 with the current account. banking-service then refuses a different transfer under a used id, so no payment is paid twice, but such a payment waits for an operator.
- A payout that was refused before this change and whose merchant has already changed the account starts at attempt 1 with the new account. banking-service refuses it the same way, so it also waits for an operator.

## When to decide again

When the settlement record of [#135](https://github.com/chrysaor/JayPay/issues/135) is built, which would hold the attempts with the payments they cover, or when a refused payout should be retried without an account change.

## Links

- Issue [#138](https://github.com/chrysaor/JayPay/issues/138)
- PR [#174](https://github.com/chrysaor/JayPay/pull/174)
- Retrospective [A refused payout could not be sent again after the merchant changed the account](../retrospectives/2026-11-27-refused-payout-bound-to-one-request-id.md)
