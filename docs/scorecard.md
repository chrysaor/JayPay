# Scorecard

Generated on 2026-11-26. Run `make scorecard` to regenerate it.

Delivery and quality numbers of [chrysaor/JayPay](https://github.com/chrysaor/JayPay), read from GitHub and the local test reports. Each number names where it comes from and what it does not measure. No number on this page is written by hand.

## Summary

| Measure | Value | Source | Limit |
| --- | --- | --- | --- |
| Merged PRs, Dependabot left out | 63 | `gh pr list --state merged`, author `app/dependabot` for Dependabot | Counts PRs, not their size or worth. One maintainer opens them with coding agents, so it is not the throughput of a team. Release and hotfix PRs into `master` count as well. |
| Merged PRs, Dependabot included | 65 | `gh pr list --state merged`, author `app/dependabot` for Dependabot | Counts PRs, not their size or worth. One maintainer opens them with coding agents, so it is not the throughput of a team. Release and hotfix PRs into `master` count as well. |
| Open to merge, median | 10 min | `createdAt` to `mergedAt` of each merged PR, Dependabot left out, nearest-rank percentile | Agents merged their own PRs once CI passed, with no human review, so this is how fast an agent got CI green, not the delivery speed of a team. Wall-clock time, nights included. The PRs of 2023 and 2024 were merged by their author within seconds of opening and pull the median down. |
| Open to merge, 90th percentile | 55 min | `createdAt` to `mergedAt` of each merged PR, Dependabot left out, nearest-rank percentile | Agents merged their own PRs once CI passed, with no human review, so this is how fast an agent got CI green, not the delivery speed of a team. Wall-clock time, nights included. The PRs of 2023 and 2024 were merged by their author within seconds of opening and pull the median down. |
| CI failure rate, all branches | 4.5% (5 of 112) | `gh api .../actions/workflows/ci.yml/runs`, the conclusion of each run | A re-run replaces the conclusion of its run, so a failure fixed by a re-run counts as a success. Cancelled runs are left out. Most runs are on pull request branches, where a red run is often work in progress, not a broken build. |
| CI failure rate, develop | 5.0% (2 of 40) | `gh api .../actions/workflows/ci.yml/runs`, the conclusion of each run | Runs on pushes to `develop`, that is after a merge. The same re-run blind spot applies. |
| Releases | 3 | `gh release list`, drafts and prereleases left out, `publishedAt` | A release is a tag with published images. The count says nothing about what each release holds or whether anyone runs it. |
| Time between releases | 4.3 h (v0.1.0 to v0.2.0), 11.2 h (v0.2.0 to v0.3.0) | `gh release list`, drafts and prereleases left out, `publishedAt` | Wall-clock time between `publishedAt` of consecutive releases. Few releases, so no trend. |
| Line coverage, backend | 85.6% (1824 of 2132 lines) | JaCoCo `testCodeCoverageReport.xml`, read by `scripts/coverage.py` | Lines that ran during the tests, not lines an assertion checks. Read from the last local `make coverage`, which may be older than this page. |
| Line coverage, web | 95.0% (133 of 140 lines) | vitest `coverage/coverage-summary.json`, read by `scripts/coverage.py` | Lines that ran during the tests, not lines an assertion checks. Read from the last local `make coverage`, which may be older than this page. |
| Line coverage, admin | 95.3% (201 of 211 lines) | vitest `coverage/coverage-summary.json`, read by `scripts/coverage.py` | Lines that ran during the tests, not lines an assertion checks. Read from the last local `make coverage`, which may be older than this page. |
| Smoke scenes | 43 | `scripts/smoke.sh`: a `log` line after a blank line or an `if ... then` line, `PASSED` left out | Counts the scenes written in the script, not a run. It does not say that they passed today. A scene that runs only on Docker Compose counts once. |
| Tests, backend | 653 | JUnit XML in `backend/**/build/test-results/test/`, tests minus skipped | Read from the last local test run, which may be older than this page. Counts test cases, not what they assert. |
| Tests, web | 39 | vitest `coverage/test-results.json`, tests minus skipped and todo | Read from the last local test run, which may be older than this page. Counts test cases, not what they assert. |
| Tests, admin | 57 | vitest `coverage/test-results.json`, tests minus skipped and todo | Read from the last local test run, which may be older than this page. Counts test cases, not what they assert. |
| Open issues | 29 | `gh issue list --state all`, pull requests left out | The maintainer and agents file the issues, so the counts show how work is filed, not how many defects users found. |
| Closed issues | 54 | `gh issue list --state all`, pull requests left out | The maintainer and agents file the issues, so the counts show how work is filed, not how many defects users found. |
| Issues labelled bug, open and closed | 8 open, 1 closed | `gh issue list --state all`, pull requests left out | Only issues someone labelled `bug`. A bug fixed inside a feature PR without its own issue is not counted. |
| Retrospectives | 3 | files in `docs/retrospectives/` other than `README.md` | Retrospectives cite the PRs that fixed the bug, not the `bug` issues, so this page cannot tell which bugs have one. |
| Open issues labelled needs-decision | 7 | `gh issue list --state all`, pull requests left out | A snapshot when the page was generated. |

## By release

Each merged PR and each CI run belongs to the first release published after it. Unreleased holds what came after the newest release. PRs carry no milestone, so the release is read from the merge time. The CI column counts only runs since `ci.yml` was added.

| Release | Merged | Merged PRs, Dependabot left out | Into master | Dependabot | Open to merge, median | Open to merge, 90th percentile | CI failure rate |
| --- | --- | --- | --- | --- | --- | --- | --- |
| v0.1.0 | 2023-12-27 to 2026-11-25 | 28 | 16 | 0 | 0 min | 22 min | 0.0% (0 of 28) |
| v0.2.0 | 2026-11-25 to 2026-11-25 | 4 | 1 | 0 | 15 min | 2.6 h | 0.0% (0 of 12) |
| v0.3.0 | 2026-11-25 to 2026-11-25 | 12 | 1 | 2 | 25 min | 56 min | 6.7% (2 of 30) |
| Unreleased | 2026-11-25 to 2026-11-26 | 19 | 0 | 0 | 13 min | 2.6 h | 7.1% (3 of 42) |

Source: `gh pr list --state merged`, author `app/dependabot` for Dependabot, `gh api .../actions/workflows/ci.yml/runs`, the conclusion of each run. Limit: Agents merged their own PRs once CI passed, with no human review, so this is how fast an agent got CI green, not the delivery speed of a team. Wall-clock time, nights included. The PRs of 2023 and 2024 were merged by their author within seconds of opening and pull the median down.

## Issues by label

An issue with several labels counts once under each of them.

| Label | Open | Closed |
| --- | --- | --- |
| architecture | 4 | 0 |
| bug | 8 | 1 |
| documentation | 3 | 1 |
| enhancement | 3 | 11 |
| infra | 6 | 0 |
| needs-decision | 7 | 0 |
| security | 6 | 0 |
| test | 4 | 0 |
| (none) | 0 | 41 |

## Issues by milestone

| Milestone | Open | Closed |
| --- | --- | --- |
| 0.4.0 | 2 | 3 |
| 0.5.0 | 9 | 0 |
| 0.6.0 | 3 | 0 |
| (none) | 15 | 51 |

Source: `gh issue list --state all`, pull requests left out. Limit: The maintainer and agents file the issues, so the counts show how work is filed, not how many defects users found.
