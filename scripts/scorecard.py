#!/usr/bin/env python3
import json
import math
import re
import statistics
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import date, datetime, timezone
from pathlib import Path
from typing import NamedTuple

import coverage

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "docs/scorecard.md"
UNRELEASED = "Unreleased"
NONE = "(none)"
DEPENDABOT = "app/dependabot"
GH_LIMIT = 2000
REBUILD_START = datetime(2026, 1, 1, tzinfo=timezone.utc)
COVERAGE_AREAS = ("backend", "web", "admin")
RUN_COVERAGE = "n/a, run `make coverage` first"


class PrStats(NamedTuple):
    people: int
    bots: int
    into_master: int
    median: object
    p90: object


class Data(NamedTuple):
    date: str
    repo: str
    prs: list
    runs: list
    releases: list
    issues: list
    coverage: dict
    tests: dict
    smoke_scenes: int
    retrospectives: int


def parse_time(text):
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def release_window(at, releases):
    return next((tag for tag, published in releases if at <= published), UNRELEASED)


def by_window(items, releases, at=lambda item: item["merged"]):
    groups = {tag: [] for tag, _ in releases}
    groups[UNRELEASED] = []
    for item in items:
        groups[release_window(at(item), releases)].append(item)
    return groups


def merged_since(prs, start):
    return [pr for pr in prs if pr["merged"] >= start]


def percentile(values, q):
    ordered = sorted(values)
    return ordered[math.ceil(q * len(ordered)) - 1]


def pr_stats(prs):
    people = [p for p in prs if not p["bot"]]
    waits = [p["merged"] - p["created"] for p in people]
    return PrStats(
        people=len(people),
        bots=len(prs) - len(people),
        into_master=sum(p["base"] == "master" for p in people),
        median=statistics.median(waits) if waits else None,
        p90=percentile(waits, 0.9) if waits else None,
    )


def ci_stats(runs):
    finished = [r for r in runs if r["conclusion"] in ("success", "failure")]
    return sum(r["conclusion"] == "failure" for r in finished), len(finished)


def rate(failed, finished):
    return f"{100 * failed / finished:.1f}% ({failed} of {finished})" if finished else "n/a"


def release_intervals(releases):
    return [(a, b, tb - ta) for (a, ta), (b, tb) in zip(releases, releases[1:])]


def duration(delta):
    if delta is None:
        return "n/a"
    minutes = delta.total_seconds() / 60
    if minutes < 60:
        return f"{round(minutes)} min"
    if minutes < 48 * 60:
        return f"{minutes / 60:.1f} h"
    return f"{minutes / 1440:.1f} d"


def issue_counts(issues, keys):
    counts = {}
    for issue in issues:
        for key in keys(issue):
            counts.setdefault(key, [0, 0])[issue["state"] != "OPEN"] += 1
    return counts


def smoke_scenes(script):
    lines = script.splitlines()
    scenes = 0
    for index, line in enumerate(lines):
        message = re.fullmatch(r'\s*log "(.*)"', line)
        if not message or message.group(1) == "PASSED":
            continue
        before = lines[index - 1].strip() if index else ""
        scenes += before == "" or before.endswith("then")
    return scenes


def junit_tests(paths):
    total = 0
    for path in paths:
        suite = ET.parse(path).getroot()
        total += int(suite.get("tests", 0)) - int(suite.get("skipped", 0))
    return total


def vitest_tests(path):
    result = json.loads(Path(path).read_text())
    return result["numTotalTests"] - result["numPendingTests"] - result["numTodoTests"]


def percent_cell(row):
    if row is None:
        return RUN_COVERAGE
    _, covered, total = row
    return f"{coverage.percent(covered, total)} ({covered} of {total} lines)"


def count_cell(value):
    return RUN_COVERAGE if value is None else str(value)


SOURCES = {
    "prs": "`gh pr list --state merged` from 2026-01-01, when the rebuild started, author `app/dependabot` for Dependabot",
    "wait": "`createdAt` to `mergedAt` of each merged PR, Dependabot left out, nearest-rank percentile",
    "ci": "`gh api .../actions/workflows/ci.yml/runs`, the conclusion of each run",
    "releases": "`gh release list`, drafts and prereleases left out, `publishedAt`",
    "backend coverage": "JaCoCo `testCodeCoverageReport.xml`, read by `scripts/coverage.py`",
    "frontend coverage": "vitest `coverage/coverage-summary.json`, read by `scripts/coverage.py`",
    "smoke": "`scripts/smoke.sh`: a `log` line after a blank line or an `if ... then` line, `PASSED` left out",
    "backend tests": "JUnit XML in `backend/**/build/test-results/test/`, tests minus skipped",
    "frontend tests": "vitest `coverage/test-results.json`, tests minus skipped and todo",
    "issues": "`gh issue list --state all`, pull requests left out",
    "retrospectives": "files in `docs/retrospectives/` other than `README.md`",
}

LIMITS = {
    "prs": "Counts PRs, not their size or worth. One maintainer opens them with coding agents, so it is not the throughput of a team. Release and hotfix PRs into `master` count as well.",
    "wait": "Agents merged their own PRs once CI passed, with no human review, so this is how fast an agent got CI green, not the delivery speed of a team. Wall-clock time, nights included. The PRs of the original project, merged in 2023 and 2024 within seconds of opening, are left out.",
    "ci all": "A re-run replaces the conclusion of its run, so a failure fixed by a re-run counts as a success. Cancelled runs are left out. Most runs are on pull request branches, where a red run is often work in progress, not a broken build.",
    "ci develop": "Runs on pushes to `develop`, that is after a merge. The same re-run blind spot applies.",
    "releases": "A release is a tag with published images. The count says nothing about what each release holds or whether anyone runs it.",
    "intervals": "Wall-clock time between `publishedAt` of consecutive releases. Few releases, so no trend.",
    "coverage": "Lines that ran during the tests, not lines an assertion checks. Read from the last local `make coverage`, which may be older than this page.",
    "smoke": "Counts the scenes written in the script, not a run. It does not say that they passed today. A scene that runs only on Docker Compose counts once.",
    "tests": "Read from the last local test run, which may be older than this page. Counts test cases, not what they assert.",
    "issues": "The maintainer and agents file the issues, so the counts show how work is filed, not how many defects users found.",
    "bug": "Only issues someone labelled `bug`. A bug fixed inside a feature PR without its own issue is not counted.",
    "retrospectives": "Retrospectives cite the PRs that fixed the bug, not the `bug` issues, so this page cannot tell which bugs have one.",
    "needs-decision": "A snapshot when the page was generated.",
}


def table(header, rows):
    lines = ["| " + " | ".join(header) + " |", "|" + "|".join(" --- " for _ in header) + "|"]
    lines += ["| " + " | ".join(str(cell) for cell in row) + " |" for row in rows]
    return "\n".join(lines)


def render(data):
    prs = pr_stats(data.prs)
    all_failed, all_finished = ci_stats(data.runs)
    develop_failed, develop_finished = ci_stats([r for r in data.runs if r["branch"] == "develop"])
    intervals = ", ".join(f"{duration(d)} ({a} to {b})" for a, b, d in release_intervals(data.releases)) or "n/a"
    open_issues = sum(i["state"] == "OPEN" for i in data.issues)
    bugs = issue_counts(data.issues, lambda i: ["bug"] if "bug" in i["labels"] else []).get("bug", [0, 0])
    needs_decision = sum(i["state"] == "OPEN" and "needs-decision" in i["labels"] for i in data.issues)

    summary = [
        ("Merged PRs, Dependabot left out", prs.people, SOURCES["prs"], LIMITS["prs"]),
        ("Merged PRs, Dependabot included", prs.people + prs.bots, SOURCES["prs"], LIMITS["prs"]),
        ("Open to merge, median", duration(prs.median), SOURCES["wait"], LIMITS["wait"]),
        ("Open to merge, 90th percentile", duration(prs.p90), SOURCES["wait"], LIMITS["wait"]),
        ("CI failure rate, all branches", rate(all_failed, all_finished), SOURCES["ci"], LIMITS["ci all"]),
        ("CI failure rate, develop", rate(develop_failed, develop_finished), SOURCES["ci"], LIMITS["ci develop"]),
        ("Releases", len(data.releases), SOURCES["releases"], LIMITS["releases"]),
        ("Time between releases", intervals, SOURCES["releases"], LIMITS["intervals"]),
        ("Line coverage, backend", percent_cell(data.coverage.get("backend")), SOURCES["backend coverage"], LIMITS["coverage"]),
        ("Line coverage, web", percent_cell(data.coverage.get("web")), SOURCES["frontend coverage"], LIMITS["coverage"]),
        ("Line coverage, admin", percent_cell(data.coverage.get("admin")), SOURCES["frontend coverage"], LIMITS["coverage"]),
        ("Smoke scenes", data.smoke_scenes, SOURCES["smoke"], LIMITS["smoke"]),
        ("Tests, backend", count_cell(data.tests.get("backend")), SOURCES["backend tests"], LIMITS["tests"]),
        ("Tests, web", count_cell(data.tests.get("web")), SOURCES["frontend tests"], LIMITS["tests"]),
        ("Tests, admin", count_cell(data.tests.get("admin")), SOURCES["frontend tests"], LIMITS["tests"]),
        ("Open issues", open_issues, SOURCES["issues"], LIMITS["issues"]),
        ("Closed issues", len(data.issues) - open_issues, SOURCES["issues"], LIMITS["issues"]),
        ("Issues labelled bug, open and closed", f"{bugs[0]} open, {bugs[1]} closed", SOURCES["issues"], LIMITS["bug"]),
        ("Retrospectives", data.retrospectives, SOURCES["retrospectives"], LIMITS["retrospectives"]),
        ("Open issues labelled needs-decision", needs_decision, SOURCES["issues"], LIMITS["needs-decision"]),
    ]

    prs_by_release = by_window(data.prs, data.releases)
    runs_by_release = by_window(data.runs, data.releases, at=lambda r: r["created"])
    releases = []
    for window, items in prs_by_release.items():
        stats = pr_stats(items)
        merged = sorted(p["merged"].date().isoformat() for p in items)
        span = f"{merged[0]} to {merged[-1]}" if merged else "n/a"
        releases.append((window, span, stats.people, stats.into_master, stats.bots, duration(stats.median), duration(stats.p90), rate(*ci_stats(runs_by_release[window]))))

    def issue_rows(counts):
        return [(key, *counts[key]) for key in sorted(counts, key=lambda k: (k == NONE, k))]

    return "\n\n".join([
        "# Scorecard",
        f"Generated on {data.date}. Run `make scorecard` to regenerate it.",
        f"Delivery and quality numbers of [{data.repo}](https://github.com/{data.repo}), read from GitHub and the local test reports. Each number names where it comes from and what it does not measure. No number on this page is written by hand.",
        "## Summary",
        table(("Measure", "Value", "Source", "Limit"), summary),
        "## By release",
        "Each merged PR and each CI run belongs to the first release published after it. Unreleased holds what came after the newest release. PRs carry no milestone, so the release is read from the merge time. The CI column counts only runs since `ci.yml` was added.",
        table(("Release", "Merged", "Merged PRs, Dependabot left out", "Into master", "Dependabot", "Open to merge, median", "Open to merge, 90th percentile", "CI failure rate"), releases),
        f"Source: {SOURCES['prs']}, {SOURCES['ci']}. Limit: {LIMITS['wait']}",
        "## Issues by label",
        "An issue with several labels counts once under each of them.",
        table(("Label", "Open", "Closed"), issue_rows(issue_counts(data.issues, lambda i: i["labels"] or [NONE]))),
        "## Issues by milestone",
        table(("Milestone", "Open", "Closed"), issue_rows(issue_counts(data.issues, lambda i: [i["milestone"] or NONE]))),
        f"Source: {SOURCES['issues']}. Limit: {LIMITS['issues']}",
    ]) + "\n"


def gh(*args):
    result = subprocess.run(["gh", *args], cwd=ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"scorecard: gh {' '.join(args)} failed: {result.stderr.strip()}")
    return result.stdout


def gh_list(*args):
    items = json.loads(gh(*args, "--limit", str(GH_LIMIT)))
    if len(items) >= GH_LIMIT:
        sys.exit(f"scorecard: gh {args[0]} {args[1]} returned {GH_LIMIT} items, raise GH_LIMIT")
    return items


def fetch_prs():
    return [
        {"created": parse_time(p["createdAt"]), "merged": parse_time(p["mergedAt"]), "bot": p["author"]["login"] == DEPENDABOT, "base": p["baseRefName"]}
        for p in gh_list("pr", "list", "--state", "merged", "--json", "createdAt,mergedAt,author,baseRefName")
    ]


def fetch_runs(repo):
    lines = gh("api", "--paginate", f"repos/{repo}/actions/workflows/ci.yml/runs?per_page=100",
               "--jq", ".workflow_runs[] | {branch: .head_branch, conclusion, created: .created_at}")
    return [{**run, "created": parse_time(run["created"])} for run in map(json.loads, lines.splitlines())]


def fetch_releases():
    releases = [
        (r["tagName"], parse_time(r["publishedAt"]))
        for r in gh_list("release", "list", "--json", "tagName,publishedAt,isDraft,isPrerelease")
        if not r["isDraft"] and not r["isPrerelease"]
    ]
    return sorted(releases, key=lambda r: r[1])


def fetch_issues():
    return [
        {"state": i["state"], "labels": [label["name"] for label in i["labels"]], "milestone": (i["milestone"] or {}).get("title")}
        for i in gh_list("issue", "list", "--state", "all", "--json", "state,labels,milestone")
    ]


def read_report(read):
    try:
        return read()
    except FileNotFoundError:
        return None


def local_coverage():
    found = {"backend": read_report(lambda: coverage.backend_rows()[0])}
    found |= {app: read_report(lambda app=app: coverage.frontend_row(app)) for app in coverage.FRONTEND_APPS}
    return found


def local_tests():
    junit = sorted(ROOT.glob("backend/**/build/test-results/test/*.xml"))
    found = {"backend": junit_tests(junit) if junit else None}
    found |= {app: read_report(lambda app=app: vitest_tests(ROOT / f"frontend/apps/{app}/coverage/test-results.json")) for app in coverage.FRONTEND_APPS}
    return found


def main():
    repo = gh("repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner").strip()
    data = Data(
        date=date.today().isoformat(),
        repo=repo,
        prs=merged_since(fetch_prs(), REBUILD_START),
        runs=fetch_runs(repo),
        releases=fetch_releases(),
        issues=fetch_issues(),
        coverage=local_coverage(),
        tests=local_tests(),
        smoke_scenes=smoke_scenes((ROOT / "scripts/smoke.sh").read_text()),
        retrospectives=sum(1 for p in (ROOT / "docs/retrospectives").glob("*.md") if p.name != "README.md"),
    )
    missing = [area for area in COVERAGE_AREAS if data.coverage[area] is None or data.tests[area] is None]
    if missing:
        print(f"scorecard: no coverage or test report for {', '.join(missing)}, run make coverage first", file=sys.stderr)
    OUTPUT.write_text(render(data))
    print(f"scorecard: wrote {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
