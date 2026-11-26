# 0015. Test coverage measured by JaCoCo and vitest, without an external service

## Release

0.4.0 (unreleased)

## Status

accepted

## Context

A visitor could not tell whether the build was green or how much of the code the tests run. The repository measured no coverage.

## Options

- An external coverage service.
- The JaCoCo plugin that ships with Gradle for the backend, one report across the modules, and vitest with `@vitest/coverage-v8` for the frontend, with CI writing the numbers to its job summary.

## Decision

No external coverage service. The `jaypay.java` convention plugin applies JaCoCo, and a root report aggregates every backend module. Both frontend apps record coverage on every `vitest run`. `make coverage` prints one table of line coverage, and the CI backend and frontend jobs write the same table to their summary. The README shows CI, Security, release and license badges.

## What we gave up

- No coverage threshold, so a drop in coverage fails nothing.
- No coverage badge with a number. The table lives in the CI job summary.
- `packages/api` and `packages/ui` are not measured. Only the apps have tests, and counting the packages in both apps would count them twice.
- JaCoCo instruments every backend test run. One run each measured `make verify` at 324 s before and 355 s after.

## When to decide again

When coverage should block a merge, a threshold has to be chosen.

## Links

- Issue [#134](https://github.com/chrysaor/JayPay/issues/134)
- PR [#141](https://github.com/chrysaor/JayPay/pull/141)
