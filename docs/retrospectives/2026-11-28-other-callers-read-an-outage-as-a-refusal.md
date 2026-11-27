# The other callers still read an outage as a refusal

## What happened

The audit of [#149](https://github.com/chrysaor/JayPay/issues/149) listed which use case has a test for an outage of a service it calls. Four had none, and the first test written for each failed. While membership-service answered 503, a payment and a remittance answered 400 as if the member were not valid, a bank account registration answered 500, and the sum of balances at an address answered 500. A remittance to a bank account answered 400 `INVALID_BANK_ACCOUNT` while banking-service failed. No money moved and nothing was stored, so the same request worked later. 0.5.0 shipped with these answers.

## Cause

[#175](https://github.com/chrysaor/JayPay/pull/175) fixed the same fault in money-service only. Each other caller has its own adapter for membership-service, and each still turned the missing body of a failed answer into "not valid". The banking adapter of remittance-service did the same for `POST /banking/info`.

## Why the checks missed it

- Decision 0018 listed the other callers under what it gave up, and [#176](https://github.com/chrysaor/JayPay/issues/176) waited for a decision with no test attached. A known gap without a failing test stayed a line of text.
- The failure path table of 0.5.0 covered the money calls of each flow. Its cell for `POST /banking/info` said "not covered", and the membership check of banking-service and money-aggregation-service was in no table.
- The fake membership-service of each test suite answered with a member or with 404, and never failed.

## Fix

[#229](https://github.com/chrysaor/JayPay/pull/229) makes each caller answer 503 and change nothing, as decision [0035](../decisions/0035-an-outage-of-a-checked-service-answers-503.md) records.

The first attempt did not look for an open decision: the fix was written as if 0018 already covered it, while #176 was open for exactly this question. The PR stayed a draft until the owner decided.

## What changed so it is caught next time

- `PaymentInvariantsTest`, `RemittanceInvariantsTest`, `RegisterBankAccountControllerTest`, `MembershipServiceAdapterTest` of banking-service and `GetMoneySumControllerTest` make the fake service fail, check the 503 and that nothing changed, and the first two send the same request again and check that it goes through once.
- `docs/test-audit.md` has an Outage column for every use case, so a use case without such a test shows as "none".
- The same fault in money-query-service loses data and is tracked as [#228](https://github.com/chrysaor/JayPay/issues/228).
