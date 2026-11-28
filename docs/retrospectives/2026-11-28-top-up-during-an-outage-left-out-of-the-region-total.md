# A top-up handled during an outage was left out of the region total

## What happened

While writing the outage tests of [#223](https://github.com/chrysaor/JayPay/issues/223), a test made the fake membership-service answer 503 for one top-up event of money-query-service. After the outage the region total was 0 where 3000 was expected. The wallet balance was right. The number an operator reads in the admin console was short, and nothing said so apart from one log line of the event processor. 0.5.0 shipped with it.

## Cause

`IncreaseMoneySumByAddressService` asked membership-service for the address once and let the failure leave the handler. The tracking processor of the group has the default error handler of Axon, which logs a failed event and goes on to the next one. So one failed call dropped the event.

## Why the checks missed it

- `RegionTotalTest` had a fake membership-service that always answered.
- The failure path table of 0.5.0 covered the calls that move money. The region total moves none, so its call to membership-service was in no table.
- Decision 0018 named money-query-service among the callers that "fail on" an outage, without saying that failing here loses data.

## Fix

[#228](https://github.com/chrysaor/JayPay/issues/228): the address is asked again every two seconds for 15 seconds, and a top-up that is still not confirmed is left out with one `ERROR`. Decision [0036](../decisions/0036-region-total-asks-for-the-address-for-a-fixed-time.md) records the choice.

The first attempt counted attempts, five of them. On the Compose stack, with membership-service paused so that each request ran into its timeout of 10 seconds, Axon interrupted the handler after 30 seconds and dropped the event the old way, without the `ERROR` of the service. The tests had only a membership-service that fails at once. The limit is now a time that fits inside the 30 seconds.

## What changed so it is caught next time

- `RegionTotalTest` makes the fake membership-service fail twice and checks that the top-up is counted, makes it fail for longer than the patience and checks that the next top-ups are still counted, and checks that a member that does not exist is asked for once.
- `docs/test-audit.md` lists the outage of membership-service for this use case.
