# The region total kept top-ups the saga rolled back

## What happened

The review of [#135](https://github.com/chrysaor/JayPay/issues/135) found it, and a test in money-query-service showed it: after a saga top-up whose charge the bank confirmed after the deadline, the region total was 3000 and the wallet had grown by 0. The admin console showed that total next to the sum of wallet balances, so the two numbers differed by every rolled-back top-up. The same review found that losing the tracking token in the `money_query` database would add every top-up again. No user or operator reported it.

## Cause

`MoneyIncreaseEventHandler` counted `RequestFirmBankingFinishedEvent` with the status `0`. That is banking-service's answer to the charge, and it comes before the saga decides whether the wallet is increased. When the deadline had already failed the wallet change, the saga rolled the charge back, but the read model had already counted it, and no later event carried the amount or the recharge id to take it out. The total was a read-modify-write of the address item with nothing that remembered which top-ups it held, so any event handled twice was added twice.

## Why the checks missed it

- `MoneyIncreaseEventHandlerTest` called the handler with the bank's answers one at a time and checked that only a confirmed charge counts. It never followed one top-up through the saga to the total.
- `RechargeSagaTest` checked the wallet and the rollback in money-service and stopped there. No test checked what another service learns from a top-up that is rolled back.
- `DynamoDBAdapterTest` never wrote the same top-up twice, and no test handled the events again from the start.
- `docs/services.md` described the total as the money that saga top-ups added, while the code counted what the bank confirmed.

## Fix

[#178](https://github.com/chrysaor/JayPay/pull/178): the saga publishes `WalletRechargedEvent` after it increased the wallet, and money-query-service counts only that event. It writes an item per recharge id in the same DynamoDB transaction that adds to the totals, so a repeated event changes nothing. The decision is in [0021](../decisions/0021-region-total-counts-wallet-increases.md). Totals written before the fix are not corrected.

## What changed so it is caught next time

- `RegionTotalTest` runs money-query-service with its event processor, DynamoDB Local and the shared `table.json`. It publishes the events of a top-up rolled back after the bank confirmed it, of a top-up whose event arrives twice, and then resets the tracking token and handles every event again, and reads each total over HTTP.
- `RechargeSagaTest` checks that a confirmed top-up publishes `WalletRechargedEvent` once, and that a charge the bank confirms after the deadline is rolled back without it.
- `DynamoDBAdapterTest` records the same top-up twice and checks the total once.
