# 0029. money-service gives back money it took, whatever the state of the member

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

A remittance that took the sender's money and then could not deliver it refunds the sender. remittance-service sent the refund as `POST /money/increase`, and money-service checks the member on every increase and stores a refusal under its request id. A refund that money-service answered with 5xx without doing it, sent again after the sender stopped being valid, was refused and stored as failed. The same request id then answered failed for good, even after the sender was valid again, so the remittance stayed `REFUNDING`, every repeat answered 503, and the sender's money stayed taken ([#184](https://github.com/chrysaor/JayPay/issues/184)). A probe against the real money-service and a test with a fake that refuses like it showed this. The issue also asks whether a remittance that already took the sender's money always finishes or refunds, as [0017](0017-payment-resumes-before-checking-parties.md) does for payments.

## Options

- money-service lets a refund of money it took through, whatever the current state of the member.
- money-service stores nothing for a change it refuses because the member is not valid, so a repeat checks again. The money stays taken for as long as the member does not become valid again.
- An operator gives the money back. This needs an admin feature of its own.

## Decision

The owner chose the first option. A member who left or was suspended still gets their own money back, payments already follow the same principle in 0017, and the top-up rollback returns a charge to the bank without looking at the member.

`POST /money/refund` takes `requestId` and `refundedRequestId`, the request id of the decrease it gives back. money-service locks that decrease, refuses with 409 `NOT_REFUNDABLE` when it is not a decrease that succeeded or when another request id already refunded it, and otherwise adds its amount to the wallet of its member without asking membership-service. The member and the amount come from the decrease, not from the caller, and the unique column `wallet_change.refunded_request_id` lets each decrease be refunded once, so the endpoint gives back only money that was taken. `POST /money/increase` and `POST /money/decrease` still refuse a member that is not valid. remittance-service sends its refund as `remittance-<remittanceId>-refund` with `refundedRequestId` `remittance-<remittanceId>-decrease`.

A refund that an earlier version stored as a refused increase under the same request id is applied when it comes back as a refund, so a remittance left `REFUNDING` before the upgrade is refunded by its next repeat.

A remittance whose debit answer was lost already finishes on the repeat without checking the members again: it ends `COMPLETED`, or `REFUNDED` when money-service refuses the credit to a receiver that is no longer valid. With this decision the refund lands too, so a remittance that took the sender's money always ends `COMPLETED` or `REFUNDED`.

## What we gave up

- A member that is not valid gets money back into their wallet, and keeps it there until they can use it again.
- A refund that money-service refuses with `NOT_REFUNDABLE` is answered by remittance-service as 503 on every repeat. Only a remittance whose debit money-service never recorded would get there.
- Nothing sends a refund in the background. A remittance left `REFUNDING` waits for the same request to be sent again, as before.
- A refund that an earlier version applied as an increase is not linked to its decrease.

## When to decide again

When a member that is not valid must not receive money back, for example a member frozen for fraud, or when remittances need to finish without the caller.

## Links

- Issue [#184](https://github.com/chrysaor/JayPay/issues/184)
- Retrospective [A remittance refund asked after the sender became invalid never landed](../retrospectives/2026-11-27-refund-refused-for-an-invalid-sender.md)
