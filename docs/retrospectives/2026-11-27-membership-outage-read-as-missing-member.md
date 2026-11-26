# A wallet change asked during a membership-service outage failed for good

## What happened

The review of [#135](https://github.com/chrysaor/JayPay/issues/135) found it, and a test against the real money-service showed it. While membership-service answered 503, a wallet change was stored as failed under its request id, and the same request id still answered failed after membership-service was back. A remittance refund asked during the outage could never land, so the remittance stayed `REFUNDING` with the sender's money taken. A payment whose debit was asked during the outage ended `FAILED`. No user or operator reported it.

## Cause

money-service read every answer of membership-service that carried no member as a member that is not valid. `CommonHttpClient` returns no body for a non-2xx answer, and `MembershipServiceAdapter` turned that missing body into "not valid", so an outage and a missing member looked the same. `ChangeMoneyService` then stored the change as `FAILED`, a final result that a repeat answers with.

The two could not be told apart from the status either. membership-service answered 500, not 404, for an id that names no member, because it loaded the member with `getReferenceById` and failed on the missing row.

## Why the checks missed it

- The fake membership-service in the money-service tests always answered, with a member or with 404. No test made it fail and then sent the same request again.
- The fakes in every service answered 404 for a missing member, so no test noticed that the real membership-service answered 500.
- The resume rules of payment-service and remittance-service rely on money-service giving no final answer when it cannot decide, and no review checked what money-service does when its own dependency fails.

## Fix

membership-service answers 404 when the id names no member. money-service reads 404 as a member that does not exist, reads a member as valid or not valid, and treats any other answer or no answer as an outage: the change answers 503 with `MEMBERSHIP_NOT_CONFIRMED`, and the claim on the request id is rolled back with the transaction. The same request sent later checks the member again and is applied once. The decision is in [0018](../decisions/0018-membership-outage-is-not-a-missing-member.md).

## What changed so it is caught next time

- `WalletInvariantsTest` makes the fake membership-service answer 503 for one member, asks an increase and a decrease, checks the 503 answer and the balance, then sends the same request after the outage and checks that it is applied once.
- `MembershipResponseTest` checks that a member id that names no member answers 404.
- payment-service, remittance-service, banking-service, money-query-service and money-aggregation-service still read a failed membership-service answer as a missing or invalid member, or fail on it. None of them stores a final result for it, and decision 0018 lists them as what was given up.
