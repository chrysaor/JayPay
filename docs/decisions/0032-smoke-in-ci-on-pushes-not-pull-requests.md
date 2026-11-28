# 0032. CI runs the smoke on pushes to `develop` and releases, not on pull requests

## Release

0.6.0

## Status

accepted

## Context

`make smoke` checks the whole Compose stack end to end, but only ran on a developer machine. CI never started the stack, so a break between services reached `develop` unless someone ran the smoke by hand. [#132](https://github.com/chrysaor/JayPay/issues/132) was found that way.

## Options

The issue named one option: a CI job that runs `make images`, `make up`, `make smoke` and `make down` on pushes to `develop` and to `release/*`, not on every pull request, and that the release workflow waits for before it publishes. For the wait, `release.yml` could either repeat the steps in its own job or call the smoke workflow.

## Decision

`.github/workflows/smoke.yml` runs the four targets on every push to `develop` and on `workflow_dispatch`. `release.yml` calls it as a reusable workflow on every push to `release/**` and every `v*` tag, and its `release` job needs it, so no image is pushed, release candidate or final, when the smoke fails. Pull requests do not run it.

The hosted `ubuntu-latest` runner has 15983 MiB. In four passing runs, the stack used 9307 to 9372 MiB once up and 9751 to 9863 MiB after the smoke, left at least 6119 MiB available and used no swap, so nothing was trimmed. The job writes `free -m` and `docker stats` before the stack, with the stack up and after the smoke to its summary. A whole job took 6 to 7.6 minutes, of which `make up` took 2.8 to 3.8 and `make smoke` 0.9 to 1.3.

When a step fails, `scripts/stack-logs.py` saves the logs of every container and `docker compose ps`, with every value of `infra/docker/.env` replaced by `<redacted NAME>`, and the job uploads them as the `stack-logs` artifact. Vault in dev mode prints its root token and its unseal key at start, so the logs cannot go out unredacted. The unseal key is not in `.env`, so the script also blanks whatever follows `Unseal Key:` and `Root Token:`. The job also masks those values in its own console log.

## What we gave up

- A pull request that breaks a call between services passes CI. The break shows on the next push to `develop`, not before the merge.
- `release.yml` smokes images it builds in the smoke job and publishes the ones its `release` job builds from the same commit, not the very same image files.

## When to decide again

When a break the smoke would have caught is merged into `develop` often enough that the smoke has to run before the merge, or when the stack outgrows the memory of the hosted runner.

## Links

- Issue [#143](https://github.com/chrysaor/JayPay/issues/143)
