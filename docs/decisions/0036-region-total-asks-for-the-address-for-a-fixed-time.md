# 0036. The region total asks for a member's address for a fixed time, then leaves the top-up out

## Release

0.6.0 (unreleased)

## Status

accepted

## Context

money-query-service adds a top-up to the total of the member's region when it handles `WalletRechargedEvent`, and asks membership-service for the member's address to do it. When membership-service answered 5xx or did not answer, the handler failed, the event processor logged the failure and moved on, and the top-up never reached the region total. A test that made the fake membership-service answer 503 for one event showed the total at 0 where 3000 was expected ([#228](https://github.com/chrysaor/JayPay/issues/228)). The wallet balance was right. Only the region total was short.

## Options

- A: try a failed event a fixed number of times, then skip it and log it as an error for an operator.
- B: stop the processor on a failed event and try it again with a growing wait until it succeeds. The retry has no end, which the owner's rule does not allow.
- C: park a failed event in a dead letter queue and handle it again later. Axon has one that needs a table.
- D: carry the address in the event, so the handler asks nobody. It changes the event of money-service.

## Decision

A. The owner chose it. It needs no table and keeps the rule that nothing retries without an end.

The limit is a time, not a count. Axon interrupts an event handler after 30 seconds and then drops the event without the service's own error. A count of five did that on the Compose stack when membership-service was paused and each request ran into its timeout of 10 seconds.

`IncreaseMoneySumByAddressService` asks for the address again every `region-total.membership-retry-after`, 2 seconds, until `region-total.membership-patience`, 15 seconds, have passed since the first attempt. The last request starts within the 15 seconds and takes at most 10, so the handler ends within 25. The events behind wait during that time. After it the top-up is left out and one `ERROR` names the recharge id, the member and the amount. A member that does not exist (404) is left out at once with a `WARN`.

## What we gave up

- An outage longer than 15 seconds still leaves top-ups out. Each of them has an `ERROR` line, and nothing adds them later.
- During a long outage every top-up event takes the full wait, so the region total falls behind by up to 25 seconds per top-up.
- The handler still depends on membership-service. Option D would remove that.

## When to decide again

When top-ups are left out in practice, or when the two reporting services become one ([#164](https://github.com/chrysaor/JayPay/issues/164)).

## Links

- Issue [#228](https://github.com/chrysaor/JayPay/issues/228)
- Decision [0021](0021-region-total-counts-wallet-increases.md)
- Retrospective [A top-up handled during an outage was left out of the region total](../retrospectives/2026-11-28-top-up-during-an-outage-left-out-of-the-region-total.md)
