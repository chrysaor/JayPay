# 0035. A request whose member or bank account cannot be checked answers 503 and leaves no flow step

## Release

0.6.0 (unreleased)

## Status

accepted

## Context

Decision [0018](0018-membership-outage-is-not-a-missing-member.md) made money-service tell an outage of membership-service from a missing member, and listed the other callers as what it gave up. [#176](https://github.com/chrysaor/JayPay/issues/176) asked which status and error each of them answers during an outage, and whether the event flow records a step for it.

The audit of [#149](https://github.com/chrysaor/JayPay/issues/149) wrote the missing tests ([#223](https://github.com/chrysaor/JayPay/issues/223)), and each failed with the answer of that time:

| Caller | Call that fails | Answer before |
| --- | --- | --- |
| payment-service | `GET /memberships/{id}`, payer or merchant | 400 `INVALID_PAYER` or `INVALID_MERCHANT` |
| remittance-service | `GET /memberships/{id}`, sender or receiver | 400 `INVALID_SENDER` or `INVALID_RECEIVER` |
| remittance-service | `POST /banking/info` | 400 `INVALID_BANK_ACCOUNT` |
| banking-service | `GET /memberships/{id}` on a registration | 500 |
| money-aggregation-service | membership-service or money-service | 500 |

None of them stored anything or moved money, but a caller got what looks like a final refusal, and the event flow of a payment and a remittance recorded a wrong reason.

## Options

- A: answer 503 with an error that names what could not be checked, store nothing and record no step in the event flow, as money-service does under 0018.
- B: A, and record a step in the event flow for the request that could not be checked, so an operator sees it in the admin console. It needs a new kind of step in event-flow-service.

## Decision

A. The owner chose it as the answer money-service already gives since 0.5.0.

- payment-service, remittance-service and banking-service read 404 from membership-service as a member that does not exist, a member as valid or not valid, and any other answer or no answer as an outage. The request answers 503 `MEMBERSHIP_NOT_CONFIRMED`.
- remittance-service reads any answer of `POST /banking/info` that is not 2xx, or no answer, as an outage. The request answers 503 `BANK_ACCOUNT_NOT_CONFIRMED`.
- money-aggregation-service answers 503 `MONEY_SUM_NOT_CONFIRMED` when membership-service or money-service does not give its part of the sum.
- Nothing is stored and no flow step is written, so the same request sent later is checked again and goes through once.

## What we gave up

- A request refused for an outage leaves no trace in the event flow or in a table. Only the `WARN` line of the caller records it.
- Nothing retries in the background. A caller that never sends the request again never gets it done.
- money-query-service is not covered. A top-up it handles during an outage of membership-service is left out of the region total, which is [#228](https://github.com/chrysaor/JayPay/issues/228).

## When to decide again

When an operator has to see the requests an outage refused, or when a caller cannot send its request again.

## Links

- Issues [#176](https://github.com/chrysaor/JayPay/issues/176) and [#223](https://github.com/chrysaor/JayPay/issues/223)
- PR [#229](https://github.com/chrysaor/JayPay/pull/229)
- Decision [0018](0018-membership-outage-is-not-a-missing-member.md)
- Retrospective [The other callers still read an outage as a refusal](../retrospectives/2026-11-28-other-callers-read-an-outage-as-a-refusal.md)
