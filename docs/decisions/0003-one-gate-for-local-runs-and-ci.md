# 0003. One gate, `make verify`, for local runs and CI

## Release

0.1.0

## Status

accepted

## Context

There was no CI. Work was to flow from issue to PR to merge to tag, and agents were to develop, test, verify and deploy with the same commands as people.

## Options

- CI jobs built from marketplace actions, with local commands that only resemble them.
- One root `Makefile` as the entry point, and one gate script that CI calls with the same targets, so CI and local commands are identical.

## Decision

The root `Makefile` is the single entry point. `make verify` runs `scripts/verify.sh`, which runs every check and prints `FAILED: <steps>` when one fails. CI calls the same target, with `STEPS` to split it into jobs. `make verify-selftest` plants failures and proves that the gate catches them. Later checks joined the same gate: the English-only check, the Helm charts, the docs and diagrams, and the changelog.

## What we gave up

- The tool image versions in `Makefile` and `scripts/verify.sh` are not covered by Dependabot.
- Docker must run for every local check, because the tools run from pinned images. See [0005](0005-tools-and-images-pinned-by-digest.md).

## When to decide again

When a check cannot run outside GitHub, it has to live in a workflow only, and the gate no longer covers everything CI checks.

## Links

- Issue [#40](https://github.com/chrysaor/JayPay/issues/40)
- PR [#48](https://github.com/chrysaor/JayPay/pull/48)
