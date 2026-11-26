# 0004. `master`, `develop` and release branches, with releases confirmed by the owner

## Release

0.1.0

## Status

accepted

## Context

Work was to flow from issue to PR to merge to tag, and a tag was to produce a release. Before the rebuild, feature branches were merged straight into `master` and nothing was tagged.

## Options

- Keep merging feature branches into `master`.
- Feature branches into `develop`, a `release/X.Y.Z` branch into `master`, and a `vX.Y.Z` tag on the merge commit.

## Decision

Feature branches go into `develop` through a PR. `make release-branch` cuts `release/X.Y.Z`, and each push to it publishes `X.Y.Z-rc.<sha>` images. The release branch goes into `master` through a PR, and the tag publishes the final images and the GitHub Release. Merging into `master` and tagging need the owner's explicit confirmation every time. An agent merges its own PR into `develop` once CI passes.

## What we gave up

- `master` requires a PR but no approval, so the confirmation is a rule in `CLAUDE.md` and not a setting that GitHub enforces.
- `Closes #<issue>` fires only on `master`, so an issue merged into `develop` is closed by hand. That rule came in 0.2.0 through [#74](https://github.com/chrysaor/JayPay/pull/74).

## When to decide again

When more than one person merges into `master`, an approval in the branch protection can replace the rule.

## Links

- Issue [#40](https://github.com/chrysaor/JayPay/issues/40)
- PR [#48](https://github.com/chrysaor/JayPay/pull/48)
