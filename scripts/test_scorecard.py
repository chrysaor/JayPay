import unittest
from datetime import timedelta

import scorecard as s

RELEASES = [("v0.1.0", s.parse_time("2026-11-25T00:00:00Z")), ("v0.2.0", s.parse_time("2026-11-25T10:00:00Z"))]


def pr(created, merged, bot=False, base="develop"):
    return {"created": s.parse_time(created), "merged": s.parse_time(merged), "bot": bot, "base": base}


class WindowTest(unittest.TestCase):
    def test_an_item_belongs_to_the_first_release_published_after_it(self):
        self.assertEqual(s.release_window(s.parse_time("2026-11-24T23:00:00Z"), RELEASES), "v0.1.0")
        self.assertEqual(s.release_window(s.parse_time("2026-11-25T00:00:00Z"), RELEASES), "v0.1.0")
        self.assertEqual(s.release_window(s.parse_time("2026-11-25T05:00:00Z"), RELEASES), "v0.2.0")
        self.assertEqual(s.release_window(s.parse_time("2026-11-26T05:00:00Z"), RELEASES), "Unreleased")

    def test_windows_list_every_release_then_unreleased_even_when_empty(self):
        groups = s.by_window([], RELEASES)
        self.assertEqual(list(groups), ["v0.1.0", "v0.2.0", "Unreleased"])


class PullRequestTest(unittest.TestCase):
    def test_merge_time_leaves_out_dependabot(self):
        prs = [
            pr("2026-11-25T00:00:00Z", "2026-11-25T00:10:00Z"),
            pr("2026-11-25T00:00:00Z", "2026-11-25T00:30:00Z"),
            pr("2026-11-25T00:00:00Z", "2026-11-25T02:00:00Z"),
            pr("2026-11-25T00:00:00Z", "2026-11-28T00:00:00Z", bot=True),
        ]
        stats = s.pr_stats(prs)
        self.assertEqual((stats.people, stats.bots, stats.into_master), (3, 1, 0))
        self.assertEqual(stats.median, timedelta(minutes=30))
        self.assertEqual(stats.p90, timedelta(hours=2))

    def test_no_pull_requests_has_no_merge_time(self):
        stats = s.pr_stats([])
        self.assertIsNone(stats.median)
        self.assertEqual(s.duration(stats.median), "n/a")

    def test_nearest_rank_percentile(self):
        values = list(range(1, 11))
        self.assertEqual(s.percentile(values, 0.9), 9)
        self.assertEqual(s.percentile([7], 0.9), 7)


class CiTest(unittest.TestCase):
    def test_failure_rate_counts_only_runs_that_succeeded_or_failed(self):
        runs = [{"conclusion": c} for c in ("success", "failure", "cancelled", "success", None, "failure")]
        self.assertEqual(s.ci_stats(runs), (2, 4))
        self.assertEqual(s.rate(2, 4), "50.0% (2 of 4)")
        self.assertEqual(s.rate(0, 0), "n/a")


class ReleaseTest(unittest.TestCase):
    def test_intervals_between_consecutive_releases(self):
        self.assertEqual(s.release_intervals(RELEASES), [("v0.1.0", "v0.2.0", timedelta(hours=10))])

    def test_duration_reads_in_minutes_hours_or_days(self):
        self.assertEqual(s.duration(timedelta(minutes=5)), "5 min")
        self.assertEqual(s.duration(timedelta(hours=4, minutes=30)), "4.5 h")
        self.assertEqual(s.duration(timedelta(days=3)), "3.0 d")


class IssueTest(unittest.TestCase):
    def test_counts_open_and_closed_per_label_and_milestone(self):
        issues = [
            {"state": "OPEN", "labels": ["bug", "infra"], "milestone": "0.4.0"},
            {"state": "CLOSED", "labels": ["bug"], "milestone": None},
            {"state": "OPEN", "labels": [], "milestone": None},
        ]
        self.assertEqual(s.issue_counts(issues, lambda i: i["labels"] or [s.NONE]), {"bug": [1, 1], "infra": [1, 0], s.NONE: [1, 0]})
        self.assertEqual(s.issue_counts(issues, lambda i: [i["milestone"] or s.NONE]), {"0.4.0": [1, 0], s.NONE: [1, 1]})


class SmokeTest(unittest.TestCase):
    def test_a_scene_is_a_log_line_that_opens_a_block(self):
        script = "\n".join([
            'log "both sites answer"',
            'request x',
            'log "member $id"',
            '',
            'log "pay a merchant"',
            'if [ "$stack" != kubernetes ]; then',
            '  log "Vault holds the key again after it restarts"',
            'fi',
            'for b in a b; do',
            '  log "$b trace"',
            'done',
            '',
            'log "PASSED"',
        ])
        self.assertEqual(s.smoke_scenes(script), 3)


class RenderTest(unittest.TestCase):
    def test_missing_reports_read_na_and_name_make_coverage(self):
        data = s.Data(
            date="2026-11-26",
            repo="owner/repo",
            prs=[pr("2026-11-24T00:00:00Z", "2026-11-24T01:00:00Z")],
            runs=[{"branch": "develop", "conclusion": "failure", "created": s.parse_time("2026-11-24T00:00:00Z")}],
            releases=RELEASES,
            issues=[{"state": "OPEN", "labels": ["needs-decision"], "milestone": None}],
            coverage={"backend": None, "web": None, "admin": None},
            tests={"backend": None, "web": None, "admin": None},
            smoke_scenes=43,
            retrospectives=2,
        )
        page = s.render(data)
        self.assertTrue(page.startswith("# Scorecard\n\nGenerated on 2026-11-26. Run `make scorecard` to regenerate it."))
        self.assertIn("| Line coverage, backend | n/a, run `make coverage` first |", page)
        self.assertIn("| CI failure rate, develop | 100.0% (1 of 1) |", page)
        self.assertIn("| Open issues labelled needs-decision | 1 |", page)
        self.assertIn("| v0.1.0 | 2026-11-24 to 2026-11-24 | 1 | 0 | 0 | 1.0 h | 1.0 h | 100.0% (1 of 1) |", page)

    def test_every_table_row_has_as_many_cells_as_its_header(self):
        page = s.render(s.Data("2026-11-26", "owner/repo", [], [], RELEASES, [], {}, {}, 0, 0))
        header = None
        for line in page.splitlines():
            if not line.startswith("|"):
                header = None
            elif header is None:
                header = line.count("|")
            else:
                self.assertEqual(line.count("|"), header, line)


if __name__ == "__main__":
    unittest.main()
