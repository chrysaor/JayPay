# 0017. A repeated payment resumes before the parties are checked

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

A payment whose debit answer is lost stays `REQUESTED`, and the caller is told to send the same request again. payment-service checked the payer and the merchant on every request before it looked for the stored payment, so a repeat after a party changed answered 400 and left the price taken from the wallet on a payment that never settled ([#136](https://github.com/chrysaor/JayPay/issues/136)). remittance-service checks the parties only when it claims a new remittance. The issue asks to resume the way remittance does, and to decide what ends a payment that can no longer be resumed: a refund path or an operator action.

## Options

- Resume a stored payment before the checks, the way remittance-service does. The issue recommends this.
- Keep the checks on every request and give the money back with a refund path when a repeat is refused.
- Keep the checks and leave the payment to an operator action.

## Decision

`PaymentService.requestPayment` looks up the payment by its request id first. A new id checks the payer and the merchant, then claims the row. A stored payment that is still `REQUESTED` calls money-service again under its debit id `payment-<paymentId>-decrease`, which money-service applies once. The repeat ends the payment `PAID` when the debit landed and `FAILED` when it was refused, so no payment is left that a repeat cannot finish, and neither a refund path nor an operator action is needed for it.

## What we gave up

- The parties are judged once, at the first request. A payment to a merchant that loses its corporate flag before the repeat still ends `PAID` and goes to settlement.
- A debit that never reached money-service is taken on the repeat even when the payer is no longer valid.
- Nothing finishes a `REQUESTED` payment in the background. A caller that never repeats leaves it `REQUESTED`, with the price taken when the debit landed. [0027](0027-unconfirmed-payments-finished-by-a-sweep.md) covers this.

## When to decide again

When a merchant or payer that loses its standing must stop a payment already started, or when payments need to finish without the caller, for example through a recovery job.

## Links

- Issue [#136](https://github.com/chrysaor/JayPay/issues/136)
- PR [#170](https://github.com/chrysaor/JayPay/pull/170)
- Retrospective [A payment whose debit answer was lost could never finish](../retrospectives/2026-11-27-payment-checks-parties-before-resuming.md)
