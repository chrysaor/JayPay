# The company bank account had four definitions in four services

## What happened

The review of [#135](https://github.com/chrysaor/JayPay/issues/135) found it while reading the code for a ledger, and [#140](https://github.com/chrysaor/JayPay/issues/140) recorded it. A top-up charge landed in one company account while its `RequestFirmBankingFinishedEvent` named a second one. Bank remittances left from that second account and settlement payouts from a third. The bank transfer rows of one day of top-ups, remittances and payouts named three company accounts, so no single account showed what JayPay held. No user or operator reported it.

## Cause

`POST /banking/firmbanking/request` took the source account from the caller, and `RequestFirmBankingCommand` took the target account from the caller. Each caller wrote the company account down as a constant of its own: money-service in `MoneyRechargeSaga`, remittance-service and settlement-service in their `BankingServiceAdapter`. banking-service had a fourth constant in `FirmBankingRequestAggregate` and used it for the charge, but copied the command's account into the event. Nothing owned the account, so nothing could keep the copies equal.

## Why the checks missed it

- Each service's tests checked its own constant. `BankTransferTest` asserted the transfer row of a charge and never the account in its event. The settlement adapter test asserted the settlement constant in the request body, and the remittance tests did not look at the source account at all.
- The fake banking-services in the remittance and settlement tests accepted any source account, as the real one did.
- The smoke test sent payouts with the settlement constant, so it repeated the divergence instead of finding it.

## Fix

The PR for [#140](https://github.com/chrysaor/JayPay/issues/140) gives banking-service the only definition, `banking.company-account` in its `application.yml`. The charge goes into it and its event names it, and `POST /banking/firmbanking/request` always sends from it. The callers no longer send a company account, and `RequestFirmBankingCommand` lost its target account. The decision is in [0020](../decisions/0020-company-account-owned-by-banking-service.md).

## What changed so it is caught next time

- `BankTransferTest` checks that a charge's event names the account the charge went into, and that a transfer request that names another source still leaves the company account.
- The settlement adapter test and `RemittanceInvariantsTest` check that the payout and the remittance send no source account.
- The smoke test reads the company account from a bank transfer and checks that the settlement payout left the same account.
- A caller cannot name the company account any more, so a new flow that sends money out has no constant to copy.
