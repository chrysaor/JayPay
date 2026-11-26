# A top-up asked during a banking-service outage failed for good

## What happened

The failure path table of [#142](https://github.com/chrysaor/JayPay/issues/142) found it, and a test against the real money-service showed it. While banking-service answered 503 to `GET /banking/account/{membershipId}`, `POST /money/increase-eda` stored the top-up as failed under its request id, as if the member had no registered account. The same request id still answered failed after banking-service was back, so the member had to send a new request. No money moved. No user or operator reported it.

## Cause

money-service read every answer of the account lookup that carried no account as a member without a registered account. `CommonHttpClient` returns no body for a non-2xx answer, and `ChangeMoneyService` turned that missing body into "no account" and stored the top-up as `FAILED`, a final result that a repeat answers with.

The two could not be told apart from the status either. banking-service answered 500, not 404, for a member without a registered account, because it called `orElseThrow()` on the empty lookup.

## Why the checks missed it

- The fake banking-service in `RechargeSagaTest` answered 404 for a member without an account and never answered 5xx. A lost answer was covered, and it answered a generic 500 only because the I/O error reached the controller.
- The fake answered 404 where the real banking-service answered 500, the same gap that [0018](../decisions/0018-membership-outage-is-not-a-missing-member.md) found for membership-service. The customer site's tests expected an empty 200 for the same case, a third answer.
- [#175](https://github.com/chrysaor/JayPay/pull/175) applied the rule to the membership check of money-service and listed the other services that still read an outage as missing, but not the other lookup of money-service itself.

## Fix

banking-service answers 404 when the member has no registered account. money-service reads 404 as no account, an account as found, and any other answer or no answer as an outage: the top-up answers 503 with `REGISTERED_ACCOUNT_NOT_CONFIRMED`, and the claim on the request id is rolled back with the transaction. The same request sent later looks the account up again and tops up once. The customer site reads 404 as a member without an account. This is the rule of decision 0018, applied to the account lookup, in [#186](https://github.com/chrysaor/JayPay/pull/186).

## What changed so it is caught next time

- `RechargeSagaTest.aRechargeWhoseAccountLookupFailsWith5xxStoresNothingAndTheRepeatCreditsOnce` makes the fake banking-service answer 503, checks the 503 answer, the error code and that nothing is stored, then sends the same request after the outage and checks one charge and one credit.
- `RechargeSagaTest.aRechargeWhoseAccountLookupAnswerIsLostStoresNothingAndTheRepeatCreditsOnce` now checks the 503 answer and the error code, not any status from 500.
- `RegisterBankAccountControllerTest.aMembershipWithoutARegisteredBankAccountIsNotFound` checks that a member without an account answers 404.
- The 5xx cell of the account lookup in the failure path table of `docs/architecture.md` names the new test.
