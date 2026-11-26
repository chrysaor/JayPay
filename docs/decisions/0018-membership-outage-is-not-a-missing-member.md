# 0018. A membership-service outage is not a missing member

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

money-service checks the member before every synchronous wallet change. Any answer of membership-service without a member counted as a member that is not valid, and the change was stored as failed under its request id. During an outage the change failed for good, and the same request id answered failed after membership-service came back ([#137](https://github.com/chrysaor/JayPay/issues/137)). membership-service itself answered 500 for an id that names no member, so the status code could not tell a missing member from an outage either. The issue asks to tell the two apart in the shared client and in the adapters, and not to store a final result for a change that failed because another service did not answer.

## Options

- Make `CommonHttpClient` throw on every 5xx answer, so each caller sees an outage as an exception.
- Keep `CommonHttpClient` as it is, since its `HttpReply` already carries the status, and read the status in the money-service membership adapter. membership-service answers 404 for a missing member.
- Keep the `wallet_change` row as pending during the outage and finish it on the repeat, instead of recording nothing.

## Decision

`CommonHttpClient` does not change. Its other callers, such as the banking adapters of remittance-service and settlement-service, already read the status, and a throw on 5xx would change what each of them does. membership-service answers 404 when the id names no member. The money-service `MembershipServiceAdapter` reads 404 as a member that does not exist, a member as valid or not valid, and any other answer or no answer as `MembershipNotConfirmedException`. The exception rolls back the transaction that claimed the request id, so nothing is recorded, and the change answers 503 with `MEMBERSHIP_NOT_CONFIRMED`. The same request sent later is a first request again, checks the member and is applied once. This is the answer that payment-service and remittance-service already treat as unconfirmed, so a payment or a remittance waiting on that change finishes on its own repeat.

## What we gave up

- No row records a change refused for an outage, so the change leaves no trace in `wallet_change` until it is sent again.
- Nothing retries in the background. A caller that never sends the request again never gets the change.
- payment-service, remittance-service, banking-service, money-query-service and money-aggregation-service still read a failed membership-service answer as a missing or invalid member, or fail on it. None of them stores a final result for it.

## When to decide again

When another service stores a final result from a membership check, or when wallet changes must finish without the caller sending them again.

## Links

- Issue [#137](https://github.com/chrysaor/JayPay/issues/137)
- PR [#175](https://github.com/chrysaor/JayPay/pull/175)
- Issue [#182](https://github.com/chrysaor/JayPay/issues/182), the same rule for the account lookup of a saga top-up on banking-service
- Retrospective [A wallet change asked during a membership-service outage failed for good](../retrospectives/2026-11-27-membership-outage-read-as-missing-member.md)
