# 0013. The saga processor's first token starts at the beginning of the stream

## Release

0.4.0

## Status

accepted

## Context

On a new cluster, a saga top-up sent right after start never reached the wallet. The saga processor of money-service created its first tracking token at the head of the event stream when it first claimed its segment. money-service reported ready while it was still retrying its connection to Axon Server, which can take up to a minute, and a top-up stored in that window sat before the token and was skipped. See the [retrospective](../retrospectives/2026-11-26-saga-skips-events-on-first-start.md).

## Options

- Make readiness wait for the processor. That only narrows the window.
- Start the first token at the tail of the stream, the oldest event, so events stored before the first claim are still read.

## Decision

`MoneyConfig` starts the first token of `MoneyRechargeSagaProcessor` at the tail of the stream. A processor that already has a token is not affected. `RechargeSagaFirstStartTest` removes the token, publishes a top-up, starts the processor and fails without the change.

## What we gave up

- A processor that starts without a token reads the whole stream. That is safe here because a new database has no saga and no wallet change to repeat, and top-ups are idempotent per request id.

## When to decide again

When another processor, whose effects are not idempotent, needs to start without a token.

## Links

- Issue [#132](https://github.com/chrysaor/JayPay/issues/132)
- PR [#133](https://github.com/chrysaor/JayPay/pull/133)
- [Retrospective](../retrospectives/2026-11-26-saga-skips-events-on-first-start.md)
