# A payment whose debit answer was lost could never finish

## What happened

The review of [#135](https://github.com/chrysaor/JayPay/issues/135) found it, and a test against the real payment-service showed it. When money-service took the price but its answer was lost, the payment stayed `REQUESTED` and the caller was told to send the same request again. If the payer stopped being valid, or the merchant stopped being valid or lost its corporate flag in the meantime, the repeat answered 400. The price stayed out of the wallet, the payment stayed `REQUESTED`, settlement never listed it, and nothing gave the money back. No user or operator reported it.

## Cause

`PaymentService.requestPayment` asked membership-service about the payer and the merchant on every request, before it looked for a payment stored under the request id. Those checks decide whether a new payment may start. Run on a repeat, they judged a payment that had already started against the parties as they are now, and refused to carry on a debit that had already happened. remittance-service looks up the stored remittance first and checks the parties only when it claims a new one, so the same rule behaved differently in the two services.

## Why the checks missed it

- `PaymentInvariantsTest` covered a repeat after a final answer and concurrent copies of one payment, but its fake money-service always answered, so no test repeated a payment that was still `REQUESTED`.
- No test changed a party between the first request and the repeat.
- The remittance fix that resumes before checking was not carried over to payment-service, and no review compared the two request paths.

## Fix

[#170](https://github.com/chrysaor/JayPay/pull/170) makes payment-service look up the payment by its request id first. Only a request with a new id checks the payer and the merchant and claims a row. A repeat of a `REQUESTED` payment calls money-service again under the same debit id, so it ends `PAID` when the debit landed and `FAILED` when it was refused. The decision is in [0017](../decisions/0017-payment-resumes-before-checking-parties.md).

## What changed so it is caught next time

- The fake money-service in `PaymentInvariantsTest` can lose the answer of a debit it applied. The tests repeat such a payment after the payer becomes invalid, the merchant becomes invalid, or the merchant loses its corporate flag, and check the answer, the balance and the settlement list. Another repeats a lost refused debit and checks that the balance is kept.
- A payment that nobody sends again still stays `REQUESTED`. That is the open part of the decision, with no issue yet.
