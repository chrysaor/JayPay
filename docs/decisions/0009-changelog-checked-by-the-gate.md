# 0009. A changelog that the gate keeps current and the release publishes

## Release

0.4.0 (unreleased)

## Status

accepted

## Context

The GitHub Release notes were a generated list of PR titles, and nothing in the repository said in words what a release added.

## Options

How a PR says that it needs no changelog line:

- A PR label. A label needs `labeled` events that rerun CI, and it cannot be checked locally.
- Path rules. They cannot tell a typo from a change in behaviour.
- A commit trailer, `Changelog: none`. It stays in the history, works with a local `make verify`, and needs one more push at most.

## Decision

`CHANGELOG.md` lists each release newest first, grouped as Added, Changed, Fixed and Security, and 0.1.0 to 0.3.0 were written from the merged PRs. The `changelog` step of `make verify` checks the format. On a PR into `develop`, CI sets `CHANGELOG_BASE=origin/develop`, and the step fails unless the branch adds a line to Unreleased, adds a version section, or has a commit with the trailer. `make release-branch` turns Unreleased into the new version, and the tag workflow publishes that section as the Release notes and fails before it builds anything when the section is missing.

## What we gave up

- The Release notes no longer list every PR. Each changelog line links its PR instead.
- A hotfix release needs its version section written by hand.

## When to decide again

When the trailer is used for changes that a user or an operator notices.

## Links

- Issue [#113](https://github.com/chrysaor/JayPay/issues/113)
- PR [#116](https://github.com/chrysaor/JayPay/pull/116)
