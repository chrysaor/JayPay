# Contributing

Everything runs from the repository root through `make`. You need Docker, a JDK that can run Gradle (21 or newer, Gradle provisions Java 25), Node 24 and Python 3. Run `make` to list the targets.

## Branches

| Branch | Role |
| --- | --- |
| main (currently named `master`) | Released code. Each release is a `vX.Y.Z` tag on a merge commit. |
| `develop` | Integration branch. Feature PRs target it. |
| `feature/<issue>-<short-name>` | One issue. Branches from `develop`, merges into `develop`. |
| `release/X.Y.Z` | Release candidate. Branches from `develop`, merges into main, then back into `develop`. |
| `hotfix/<issue>-<short-name>` | Urgent fix. Branches from main, merges into main and `develop`. |

Nobody pushes to main or `develop` directly. Merging into main needs one approval.

## Making a change

1. Open an issue, or pick one.
2. `git switch -c feature/<issue>-<short-name> origin/develop`
3. Write a failing test, then make it pass.
4. `make verify`. It runs every check CI runs and prints `FAILED: <steps>` when something breaks.
5. Open a PR against `develop` with the template filled in and `Closes #<issue>`.
6. Merge after CI passes and the review is approved.

## Releasing

1. `make release-branch VERSION=X.Y.Z`, then `git push -u origin release/X.Y.Z`.
2. Each push to the release branch runs `release.yml`. It verifies, builds every image, fails on HIGH or CRITICAL findings, and publishes `ghcr.io/chrysaor/jaypay-<name>:X.Y.Z-rc.<sha>`. Deploy the candidate and check it.
3. Open a PR from `release/X.Y.Z` into main. It needs one approval.
4. After the merge, tag the merge commit and push the tag:
   `git tag vX.Y.Z <merge-commit> && git push origin vX.Y.Z`
   `release.yml` rebuilds and scans the images, publishes `ghcr.io/chrysaor/jaypay-<name>:vX.Y.Z`, and creates the GitHub Release with generated notes.
5. Merge `release/X.Y.Z` back into `develop`.

## Running locally

```
make images
cp infra/docker/.env.example infra/docker/.env
make up
make down
```

Fill in every value in `infra/docker/.env` first. `make up` and `make down` stop with a message while the file is missing.

`make scan` runs the Trivy filesystem scan and the gitleaks history scan from `SECURITY.md`.
