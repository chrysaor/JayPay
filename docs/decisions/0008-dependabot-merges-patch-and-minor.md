# 0008. Dependabot patch and minor updates merge by themselves

## Release

0.3.0

## Status

accepted

## Context

Security fixes waited in open Dependabot PRs until somebody merged them.

## Options

- Merge every Dependabot PR by hand.
- Turn on auto-merge for patch and minor updates once the required checks pass, and leave major updates for a person.

## Decision

`dependabot-auto-merge.yml` turns on auto-merge for a Dependabot PR into `develop` when its highest change is a patch or a minor update, and GitHub merges it once every required check has passed. Major updates and updates whose type cannot be read wait for a person. `master` is untouched, so a release still needs the owner's confirmation. Dependabot also waits 5 days after an upstream release before it proposes it.

## What we gave up

- A person no longer reads every patch and minor update before it reaches `develop`. The required checks are the only review.

## When to decide again

When a patch or minor update that passed the checks breaks `develop`.

## Links

- Issue [#89](https://github.com/chrysaor/JayPay/issues/89)
- PR [#92](https://github.com/chrysaor/JayPay/pull/92)
