# Retrospectives

One file for each bug that was worth learning from. The point is the last two sections: why the checks missed it, and what changed so the same kind of bug is caught next time.

## When to write one

Write a retrospective in the PR that fixes the bug when any of these is true:

- The bug reached `develop` or a release.
- `make verify` and CI passed with the bug in place.
- The fix took more than one attempt.
- The same kind of bug has shown up before.

A typo or a bug caught by the first test run does not need one.

## Format

Name the file `<date>-<short-name>.md` and add it to the list below. Use these sections:

| Section | What it says |
| --- | --- |
| What happened | What a user or an operator saw, and how it was found |
| Cause | The first place the system went wrong |
| Why the checks missed it | Which test, gate or review should have caught it and why it did not |
| Fix | What changed, with the PR |
| What changed so it is caught next time | A test, a gate step or a rule, or the issue that tracks it |

Keep it to one page. Leave out secrets, account values and request examples that would work as an attack.

## List

| Date | Retrospective | Fixed in |
| --- | --- | --- |
| 2026-11-26 | [A top-up asked right after a first start was never processed](2026-11-26-saga-skips-events-on-first-start.md) | [#133](https://github.com/chrysaor/JayPay/pull/133) |
| 2026-11-26 | [An existing event flow database refused the steps of new flows](2026-11-26-event-flow-schema-refuses-new-steps.md) | [#145](https://github.com/chrysaor/JayPay/pull/145) |
| 2026-11-26 | [Two tests that passed or failed by timing](2026-11-26-tests-that-depend-on-timing.md) | [#133](https://github.com/chrysaor/JayPay/pull/133), [#145](https://github.com/chrysaor/JayPay/pull/145) |
| 2026-11-26 | [event-flow-service was killed at its memory limit on the cluster](2026-11-26-event-flow-service-killed-at-its-memory-limit.md) | [#157](https://github.com/chrysaor/JayPay/pull/157) |
| 2026-11-27 | [The 0.4.0 release branch failed its own checks three times](2026-11-27-release-branch-failed-its-own-checks.md) | [#158](https://github.com/chrysaor/JayPay/pull/158) |
| 2026-11-27 | [A payment whose debit answer was lost could never finish](2026-11-27-payment-checks-parties-before-resuming.md) | [#170](https://github.com/chrysaor/JayPay/pull/170) |
| 2026-11-27 | [A wallet change asked during a membership-service outage failed for good](2026-11-27-membership-outage-read-as-missing-member.md) | [#175](https://github.com/chrysaor/JayPay/pull/175) |
| 2026-11-27 | [A refused payout could not be sent again after the merchant changed the account](2026-11-27-refused-payout-bound-to-one-request-id.md) | [#174](https://github.com/chrysaor/JayPay/pull/174) |
| 2026-11-27 | [The company bank account had four definitions in four services](2026-11-27-company-account-defined-in-four-services.md) | [#140](https://github.com/chrysaor/JayPay/issues/140) |
