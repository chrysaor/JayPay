# Two tests that passed or failed by timing

## What happened

Two tests of the top-up saga in money-service failed now and then while the code they test was correct.

- `RechargeSagaFirstStartTest` passed once locally, was pushed, and failed in CI.
- `RechargeSagaTest` failed about once in six runs. It was on `develop` for a day and failed one `make verify` of an unrelated change, where it was first put down to another build running at the same time.

## Cause

- The first test stopped the saga processor while it was still starting. A processor stopped at that moment ignores the next start, so the test waited for work that never began.
- The second test expected two flow steps to arrive on Kafka in the order they happened. The first step is published after its transaction commits, so the saga can publish the next step first. The event flow orders steps by the time they happened, so the product was right and the test was asserting something the product never promised.

## Why the checks missed it

One passing run was taken as proof. Both failures need a thread to win a race, which one run rarely shows. The first report of the second failure was explained away without reproducing it.

## Fix

[#133](https://github.com/chrysaor/JayPay/pull/133) waits until the processor has claimed its segment before stopping it. [#145](https://github.com/chrysaor/JayPay/pull/145) reads the steps in the order they happened.

## What changed so it is caught next time

- A new test, and any test that waits on a processor, Kafka, a scheduler or polling, is run at least five times before the push.
- A test that fails once is reproduced by repeating it before anyone names a cause. "Another build was running" is a guess until a run shows it.
- A test asserts what the product promises. For events that is the order by their own time, not the order of arrival.
