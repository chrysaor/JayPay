# 0039. No mutation testing for now

## Release

0.6.0

## Status

accepted

## Context

The audit of [#149](https://github.com/chrysaor/JayPay/issues/149) lists which behaviour of each use case has a test (`docs/test-audit.md`). Line coverage and that table do not say whether a test would notice a tested line changing. Mutation testing does, and it needs a new build plugin, PIT, which the owner has to approve like every new library.

## Options

- A: do not add it now.
- B: add PIT to the backend build in 0.6.0. The build takes longer.

## Decision

A. The owner chose it. The audit already showed where tests are missing, and writing those tests found a fault each time, so the gaps it lists come first.

## What we gave up

- No number says how many of the covered lines a test checks. A test that runs a line without asserting on it still counts as coverage.

## When to decide again

When the table of `docs/test-audit.md` has no "none" left in a case that applies, or when a fault ships in code that a test covers.

## Links

- Issue [#149](https://github.com/chrysaor/JayPay/issues/149)
