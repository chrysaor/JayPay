# A top-up asked right after a first start was never processed

## What happened

On a new Kubernetes cluster, the smoke failed on its first run: a saga top-up stayed pending and the wallet never changed. A second run minutes later passed. The Compose stack never showed it.

## Cause

The saga processor of money-service creates its tracking token the first time it claims its segment, and Axon creates that token at the head of the event stream. money-service reported ready while it was still retrying its connection to Axon Server, which takes up to a minute on a new cluster. A top-up asked in that window was stored before the token existed, so the processor started after it and skipped it.

## Why the checks missed it

- The integration tests run without Axon Server, so the processor claims its segment at once and the window does not exist.
- The Compose stack starts Axon Server long before the services, so the window is too short to hit.
- Nothing tested the first start of a processor on an empty token store. Every test ran against a processor that was already following the stream.

## Fix

[#133](https://github.com/chrysaor/JayPay/pull/133) makes the first token of the saga processor start at the tail of the stream, so events stored before the first start are still read.

## What changed so it is caught next time

- `RechargeSagaFirstStartTest` deletes the token, publishes a top-up and then starts the processor.
- The Kubernetes smoke runs right after a first start before a PR that touches startup is merged.
- [#143](https://github.com/chrysaor/JayPay/issues/143) puts the smoke into CI, so a break between services no longer depends on someone running it by hand.
