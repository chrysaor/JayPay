# Contributing

Everything runs from the repository root through `make`. You need Docker, a JDK that can run Gradle (21 or newer, Gradle provisions Java 25), Node 24 and Python 3. Run `make` to list the targets.

## Branches

| Branch | Role |
| --- | --- |
| `master` | Released code. Each release is a `vX.Y.Z` tag on a merge commit. |
| `develop` | Integration branch. Feature PRs target it. |
| `feature/<issue>-<short-name>` | One issue. Branches from `develop`, merges into `develop`. |
| `release/X.Y.Z` | Release candidate. Branches from `develop`, merges into `master`, then back into `develop`. |
| `hotfix/<issue>-<short-name>` | Urgent fix. Branches from `master`, merges into `master` and `develop`. |

Nobody pushes to `master` or `develop` directly; every change goes through a PR. `master` requires a PR but no approval, because the repository owner opens the PRs and cannot approve their own.

Who merges:

- Into `develop`: an agent merges once CI passes.
- Dependabot PRs into `develop`: patch and minor updates merge by themselves once the required checks pass. Major updates wait for a person.
- Into `master`, and every `vX.Y.Z` tag: an agent acts only after the owner confirms explicitly in the conversation. Without that, nobody merges into `master` or tags.

## Making a change

1. Open an issue, or pick one. A new issue gets at least one label (`bug`, `enhancement`, `documentation`, `test`, `security`, `infra`, `architecture` or `needs-decision`) and the milestone of the release it targets.
2. `git switch -c feature/<issue>-<short-name> origin/develop`
3. Write a failing test, then make it pass.
4. Add a line to Unreleased in `CHANGELOG.md`, see [Changelog](#changelog). A PR that makes a decision also adds a record to [docs/decisions](docs/decisions/README.md) that names the release it goes into.
5. `make verify`. It runs every check CI runs and prints `FAILED: <steps>` when something breaks. Its `charts` step renders every workload in `deploy/envs` and fails when `deploy/envs/local` and `infra/docker/compose.yaml` disagree, so a change to a workload's environment or `mem_limit` in one needs the same change in the other.
6. Open a PR against `develop` with the template filled in and `Closes #<issue>`.
7. Merge after CI passes.
8. Close the issue. `Closes` only fires on `master`, so merging into `develop` leaves it open.

## Logging

Each backend log line takes the level of what it tells the operator:

| Level | Meaning | Example |
| --- | --- | --- |
| `ERROR` | An operator has to act: money may be wrong, or the system gave up | The rollback of a top-up failed, a bank account registration was lost, a merchant cannot be paid |
| `WARN` | Something outside the service failed, and the system recovers by itself through a retry, a deadline or the next run | banking-service did not answer the transfer |
| `INFO` | One line for each business state change, and for each expected refusal with its reason | Member 7 has no registered account, banking-service refused the transfer as invalid |
| `DEBUG` | Detail of one call, off by default | The command a handler received, each record sent to the logging topic |

- An expected refusal, such as a member that is not valid, no registered account, a charge the bank refused or a transfer refused as invalid, is `INFO`, not `WARN`, so the count of `WARN` lines says that something is broken.
- A refusal is expected only when the caller who asked gets it as the answer. A refusal inside a scheduled run, such as a payout the bank refused or a merchant without a bank account, reaches nobody, so it is `ERROR`.
- Every line names the id of what it is about. The backend build fails on a log call with a fixed text and no argument.
- A caught exception goes in as the last argument, so the line keeps its class and stack trace. Never log only its `getMessage()`.
- Never log a bank account number, or a whole request or command object.
- A failure is logged once, where it is caught. A web error handler that turns an exception into 4xx or 503 logs nothing more.

Every service runs at the Spring Boot default, `INFO`. [docs/services.md](docs/services.md#log-levels) says how to raise the level of one service.

## Messages between releases

A release must work in any order of the services, because a service of the release before keeps running next to it during the rollout. Every service ignores a field it does not know in an Axon event or command, so a field may be added. A reader of the release before still has to do the right thing without it, and a new meaning that it must not miss, such as a new outcome, goes in a new event type, which the release before skips. Never remove a field that a reader of the release before needs, nor rename a message class.

## Changelog

`CHANGELOG.md` tells a reader what each release lets them do that the one before did not. Every PR into `develop` that changes behaviour adds a line under `## [Unreleased]`, in the group that fits: `### Added`, `### Changed`, `### Fixed` or `### Security`, in that order. Write what changed for someone who uses or runs JayPay, not the PR title, and end the line with its PR or issue link, for example `([#107](https://github.com/chrysaor/JayPay/pull/107))`.

A change that needs no entry, such as a typo, a CI internal or a test-only change, says so with the trailer `Changelog: none` at the end of one of its commit messages.

The `changelog` step of `make verify` checks the format. On a PR into `develop`, CI also sets `CHANGELOG_BASE=origin/develop`, and the step then fails unless the branch adds a line to Unreleased, adds a version section (a release merged back), or has a commit with that trailer. Run `CHANGELOG_BASE=origin/develop make verify STEPS=changelog` to check a branch before you push it. Dependabot PRs and PRs into `master` skip this check.

## Releasing

1. `make release-branch VERSION=X.Y.Z`, then `git push -u origin release/X.Y.Z`. It stops before it does anything when the working tree or the index holds uncommitted changes, which would end up in the release commit, and it fails when Unreleased is empty. Otherwise the branch starts with one commit, `chore: release X.Y.Z`, that sets the backend version and turns Unreleased into `## [X.Y.Z] - <today>` with an empty Unreleased above it.
2. On the release branch, turn `## X.Y.Z (unreleased)` in [docs/news.md](docs/news.md) into `## X.Y.Z (<today>)` and complete that section and the In progress table, and drop `(unreleased)` from the decision records of the release and from the index in `docs/decisions/README.md`. `make release-branch` does not change these files. Commit and push.
3. Each push to the release branch runs `release.yml`. It verifies, builds every image, fails on HIGH or CRITICAL findings, and publishes `ghcr.io/chrysaor/jaypay-<name>:X.Y.Z-rc.<sha>`. Deploy the candidate and check it.
4. Open a PR from `release/X.Y.Z` into `master`, and merge it once the owner confirms.
5. After the merge, and with the owner's confirmation, tag the merge commit and push the tag:
   `git tag vX.Y.Z <merge-commit> && git push origin vX.Y.Z`
   `release.yml` rebuilds and scans the images, publishes `ghcr.io/chrysaor/jaypay-<name>:vX.Y.Z`, and creates the GitHub Release with the `## [X.Y.Z]` section of `CHANGELOG.md` as its notes. Both the release branch and the tag runs read that section first and fail before they build anything when it is missing. Creating the Release is retried, and a rerun passes when the Release exists.
6. Merge `release/X.Y.Z` back into `develop`.

## Running locally

```
make images
make up
make smoke
make down
```

The first `make up` writes `infra/docker/.env` with random secrets. `make smoke` drives the running stack end to end.

`make scan` runs the Trivy filesystem scan and the gitleaks history scan from `SECURITY.md`.
