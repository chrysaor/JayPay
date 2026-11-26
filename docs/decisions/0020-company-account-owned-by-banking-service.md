# 0020. banking-service owns the company bank account

## Release

0.5.0 (unreleased)

## Status

accepted

## Context

The company bank account had four definitions ([#140](https://github.com/chrysaor/JayPay/issues/140)). banking-service sent each top-up charge into its own constant and ignored the account in `RequestFirmBankingCommand`, money-service put a different account in that command, which banking-service copied into `RequestFirmBankingFinishedEvent`, and remittance-service and settlement-service each sent their own constant as the source of a transfer. Money came in on one account and left from two others, and the event of a charge named an account its transfer row did not. The issue sets the direction: banking-service owns the company accounts and callers name a purpose, not an account. It comes before the ledger work of [#135](https://github.com/chrysaor/JayPay/issues/135).

## Options

- banking-service owns the account and callers name a purpose. The issue recommends this.
- Within it, a `purpose` field in `POST /banking/firmbanking/request` that picks an account. Rejected in this PR: one account backs every flow, so the field would pick nothing. The call names the purpose: `RequestFirmBankingCommand` is a charge into the company account and `POST /banking/firmbanking/request` is a transfer out of it.
- Within it, the account in an environment variable. Rejected in this PR: the value is not a secret, since four services kept it in their source, and a value in `application.yml` needs no change to `compose.yaml` or the charts.

## Decision

banking-service reads the company account from `banking.company-account.bank-name` and `banking.company-account.bank-account-number` in its `application.yml`, with the value it already sent charges into, so the charges and the refunds stored before keep their account. `FirmBankingRequestAggregate` sends each charge into it and names it in `RequestFirmBankingFinishedEvent`. `POST /banking/firmbanking/request` takes `requestId`, `toBankName`, `toBankAccountNumber` and `moneyAmount`, ignores a source account in the body, and always sends from the company account. money-service, remittance-service and settlement-service no longer hold an account constant, and `RequestFirmBankingCommand` lost `toBankName` and `toBankAccountNumber`. It ignores unknown fields, so a command from a money-service built before this change still lands.

A transfer stored before this change names the source its caller sent. When the same id comes again with the same receiver and amount, banking-service answers it as a repeat and sends the stored transfer, so a payout or a remittance that was unanswered or whose answer was lost during the deploy still finishes once.

## What we gave up

- `POST /banking/firmbanking/request` can no longer move money between two accounts that are not the company's. No caller did.
- Transfer rows and `RequestFirmBankingFinishedEvent` events stored before this change keep the account their caller named. Nothing rewrites them.
- One account serves every flow. A separate account per flow needs a change in banking-service only, but it is still a code change.
- The account is the same in every environment until it moves to an environment variable.

## When to decide again

When the ledger of [#135](https://github.com/chrysaor/JayPay/issues/135) needs a separate account for charges, remittances or payouts, or when an environment needs another company account.

## Links

- Issue [#140](https://github.com/chrysaor/JayPay/issues/140)
- Retrospective [The company bank account had four definitions in four services](../retrospectives/2026-11-27-company-account-defined-in-four-services.md)
