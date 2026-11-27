# A member who registered another bank account kept the first one

## What happened

The test audit of [#149](https://github.com/chrysaor/JayPay/issues/149) listed the repeated registration of a bank account as a case without a test, and the first test of it, written for [#225](https://github.com/chrysaor/JayPay/issues/225), failed. A member who registered a second account was answered with the new account, and `GET /banking/account/{membershipId}` still answered the first one. Top-ups and settlement payouts read the account through that lookup, so both kept using the first account. Decision [0019](../decisions/0019-payout-attempts-in-settlement-service.md) relies on a merchant registering another account after a refused payout. No user or operator reported it.

## Cause

Every registration adds a row, and the lookup read all rows of the member with a query that had no order and took the first of them. In the test against PostgreSQL that was the oldest row.

## Why the checks missed it

- No test registered an account twice for one member. `RegisterBankAccountControllerTest` and `BankingPersistenceTest` each stored one account and read it back.
- `SettlementAfterAccountChangeTest` and `SettlementServiceTest` change the account of a merchant in a fake banking-service, which answers whatever account the test sets. They show that settlement-service follows a changed account, not that banking-service reports one.
- The smoke test registers one account for each member.

## Fix

The lookup answers the account the member registered last, the row with the highest id, in [#225](https://github.com/chrysaor/JayPay/issues/225).

## What changed so it is caught next time

- `RegisterBankAccountControllerTest.theAccountAMemberRegistersNextIsTheOneThatIsFound` registers two accounts for one member against the real database and checks that the lookup answers the second.
- `RegisterBankAccountControllerTest.registersNothingForAMemberThatMembershipDoesNotConfirm` and `RegisterBankAccountServiceTest.registersNothingForAnAccountTheBankReportsAsNotValid` cover the two refusals of a registration.
- `docs/test-audit.md` names these tests in the cells that said none.
