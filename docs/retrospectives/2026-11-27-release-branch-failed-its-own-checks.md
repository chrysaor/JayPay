# The 0.4.0 release branch failed its own checks three times

## What happened

The release PR of 0.4.0 failed CI three times before it could be merged. Nothing was wrong with the product. The first push failed the gate selftest and one admin console test, and the fix for that test failed the lint.

## Cause

- `make verify-selftest` released the Unreleased section of the real `CHANGELOG.md` to test the changelog script. On a release branch that section is empty, because `make release-branch` has just moved it. The changelog gate was new in 0.4.0, so the selftest had never run on a release branch.
- An admin console test asserted that a request had been sent as soon as the screen heading showed. The screen sends that request after the heading, so the test passed or failed by timing.
- The fix for that test was pushed after running the tests only. The lint, which is part of the same gate step, was not run.

## Why the checks missed it

Every check ran on `develop` and on feature branches, where Unreleased always has entries. No check runs the gate against a changelog in the state a release branch has. The timing test had passed on every earlier run. The third failure was not missed by a check, it was a push without the gate.

## Fix

[#158](https://github.com/chrysaor/JayPay/pull/158): the selftest plants its own Unreleased entry, so it passes whatever the real changelog holds, and the admin test waits for the request.

## What changed so it is caught next time

- The selftest no longer depends on the state of the real changelog.
- A change to the frontend is pushed after `make verify STEPS=frontend`, which runs lint, tests and build, and not after the tests alone.
- [#149](https://github.com/chrysaor/JayPay/issues/149) looks for the other tests that assert before the thing they wait for has happened.
