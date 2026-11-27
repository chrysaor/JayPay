# Checks that passed without checking

## What happened

A review of the scripts, the `Makefile` and the workflows ([#208](https://github.com/chrysaor/JayPay/issues/208)) found checks that stayed green while the thing they guard was broken, and a release target that could commit local changes.

- A PR that changed only `infra/terraform/modules/dynamodb-money-sum/table.json` skipped the backend job, although the money-query-service tests read that file.
- The `docs` step of the gate never ran in CI. Only `make verify-selftest` ran it, on a clean copy.
- Five smoke assertions passed on any failure: the staff token sent to the customer site passed unless the answer was `true`, the writes of the ClickHouse `grafana` user passed unless the answer was 200, and the three refused staff sign-ins passed when the sign-in script crashed or got an HTTP error.
- `make release-branch` carried uncommitted changes to `CHANGELOG.md` or the version file into the release commit.
- The smoke test on Kubernetes waited without a limit for `dynamodb-0` to come back.

None of these broke the product. Each one would have hidden a break.

## Cause

Each check was written against the answer it expected and never run against a wrong one. A check that says "not `true`" or "not 200" also passes on 404, 500 and no answer. The CI path filter and the `STEPS` list were written by hand next to the things they mirror, `backend/` reads and `ALL_STEPS` in `scripts/verify.sh`, and nothing compared them.

## Why the checks missed it

`make verify-selftest` plants faults in the gate steps, but not in CI's split of them, and the smoke test has no selftest. A path filter only shows its gap on a PR that touches the missing path alone, which had not happened. `make release-branch` had only run from a clean checkout.

## Fix

[#208](https://github.com/chrysaor/JayPay/issues/208):

- The backend filter lists `table.json`. A copy of the repository that holds only `backend/` and `frontend/` builds and tests both, except for the money-query-service tests that read that file, so it is the only file outside its directory that either reads.
- CI runs `docs`.
- The smoke assertions require the refusal they expect: HTTP 200 with `false` from `token-validate`, HTTP 403 with `ACCESS_DENIED` from ClickHouse, and exit status 3 from `scripts/staff_sign_in.py`, which it now uses only when the Keycloak sign-in page shows an error.
- `make release-branch` stops when the working tree or the index has uncommitted changes.
- The wait for `dynamodb-0` gives up after 60 seconds.

## What changed so it is caught next time

- `make verify-selftest` fails when the steps CI runs differ from `ALL_STEPS`.
- A new smoke assertion names the status and body it expects instead of the one it rules out.
