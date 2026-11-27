# 0021. The region total counts wallet increases, once per recharge

## Release

0.5.0

## Status

accepted

## Context

money-query-service added to the region total on each `RequestFirmBankingFinishedEvent` with the status `0` from banking-service. That event comes before the saga decides whether the wallet is increased. When the deadline has already failed the wallet change, the saga rolls the charge back and the wallet stays as it was, but the total kept the amount. `RollbackFirmBankingFinishedEvent` carries neither the amount nor the recharge id, so the total could not be corrected from it ([#139](https://github.com/chrysaor/JayPay/issues/139)). The review of [#135](https://github.com/chrysaor/JayPay/issues/135) also found that the total is not idempotent: if the tracking token in the `money_query` database is lost, every top-up is added again.

## Options

The issue names two:

- Count a top-up from an event that says the wallet was increased.
- Give the rollback event what is needed to subtract. Rejected in this PR: the saga also sends a rollback for a charge the bank never confirmed, which the total never counted, so the rollback alone does not say whether to subtract. It would also change the event of banking-service and every place that builds it.

## Decision

money-service publishes `WalletRechargedEvent`, with the recharge id, the member and the amount, from the saga right after `finishRecharging` increased the wallet. A rollback publishes nothing. money-query-service handles only this event. For each one it writes an item `recharge#<rechargingRequestId>` and adds the amount to the day total and the overall total of the member's address, in one DynamoDB transaction that fails when the item already exists. A failed condition means the top-up is already counted, and the handler moves on. The saga can publish the event twice when it handles the bank's answer again, and a replay after a lost token handles every event again, and in both cases the totals stay as they were. The table definition in `table.json` is unchanged.

## What we gave up

- Totals written before this release keep the charges the saga rolled back. They are not corrected, and correcting them means recomputing them from the wallet changes of saga top-ups or starting the table again, which the owner has not chosen.
- A top-up that is in flight during the upgrade can be counted twice, by the old handler on the bank's answer and by the new one on the wallet event, or missed, when money-query-service is upgraded before money-service.
- The table gains one item per top-up, so it grows with the number of top-ups instead of the number of addresses and days.
- The contract between money-service and money-query-service lives in `libs/common`, like the other saga events.

## When to decide again

When the two reporting services become one ([#164](https://github.com/chrysaor/JayPay/issues/164)), which has to choose which number to keep, or when the ledger of [#135](https://github.com/chrysaor/JayPay/issues/135) records top-ups.

## Links

- Issue [#139](https://github.com/chrysaor/JayPay/issues/139)
- PR [#178](https://github.com/chrysaor/JayPay/pull/178)
- Retrospective [The region total kept top-ups the saga rolled back](../retrospectives/2026-11-27-region-total-kept-rolled-back-top-ups.md)
