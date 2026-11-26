# A refused payout could not be sent again after the merchant changed the account

## What happened

The review of [#135](https://github.com/chrysaor/JayPay/issues/135) found it, and a test against the real banking-service showed it. When the bank refused a settlement payout, the payment stayed `PAID`, and `docs/services.md` told the operator to fix the merchant's account and settle again. After the merchant registered another account, every settlement run got 409 `REQUEST_ID_REUSED` from banking-service, logged an unconfirmed payout every 30 seconds, and never settled the payment. No user or operator reported it.

## Cause

`SettlementService` sent every payout under `settlement-<paymentId>`, a request id fixed for the life of the payment, together with the merchant's account read at each run. banking-service binds a request id to the first transfer it sees, so the id could carry only the first account. The account was an input that changes, tied to an id that cannot.

## Why the checks missed it

- The fake banking-service in `SettlementServiceTest` answered a request id with its first answer whatever account came with it, and the one in `SettlementJobTest` kept only the first transfer of an id. Neither answered 409 for another transfer under a used id, as the real one does.
- No test changed a merchant's account between two runs.
- The manual fix in `docs/services.md` was written without being tried.

## Fix

The PR for [#138](https://github.com/chrysaor/JayPay/issues/138) adds the table `payout_attempt` to settlement-service. Each payment has one current attempt with its own request id and the account it is sent to. A run sends the stored attempt with the stored account, and starts the next attempt with the new account only after banking-service answered that the current one failed. The decision is in [0019](../decisions/0019-payout-attempts-in-settlement-service.md).

## What changed so it is caught next time

- The fake banking-service in `SettlementServiceTest`, and the one in the new `SettlementAfterAccountChangeTest`, bind a request id to its first transfer and answer another transfer under it with an unconfirmed payout or 409, and both let the bank take a transfer whose answer is lost.
- `SettlementAfterAccountChangeTest` runs the job against the real database and changes the merchants' accounts after a refused payout, a payout whose settlement answer was lost and an unanswered payout, then runs it four times at once and twice more. It checks that every payment is settled and that the bank received each payout once, the refused one again only to the new account.
- `SettlementServiceTest` covers a second refusal followed by a third account, and an account change while the bank still does not answer.
