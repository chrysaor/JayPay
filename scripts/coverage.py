#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BACKEND_REPORT = ROOT / "backend/build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml"
FRONTEND_APPS = ("web", "admin")


def backend_rows():
    report = ET.parse(BACKEND_REPORT).getroot()
    lines = next(c for c in report.findall("counter") if c.get("type") == "LINE")
    covered = int(lines.get("covered"))
    return [("backend", covered, covered + int(lines.get("missed")))]


def frontend_rows():
    rows = []
    for app in FRONTEND_APPS:
        summary = json.loads((ROOT / f"frontend/apps/{app}/coverage/coverage-summary.json").read_text())
        lines = summary["total"]["lines"]
        rows.append((f"frontend {app}", lines["covered"], lines["total"]))
    return rows


AREAS = {"backend": backend_rows, "frontend": frontend_rows}


def percent(covered, total):
    return f"{100 * covered / total:.1f}%" if total else "n/a"


def main(areas):
    unknown = [a for a in areas if a not in AREAS]
    if unknown:
        sys.exit(f"unknown area: {' '.join(unknown)} (known: {' '.join(AREAS)})")
    rows = []
    for area in areas:
        try:
            rows += AREAS[area]()
        except FileNotFoundError as missing:
            sys.exit(f"coverage: no {area} report at {missing.filename}, run make coverage")
    print("### Line coverage\n")
    print("| Area | Lines covered | Line coverage |")
    print("| --- | ---: | ---: |")
    for name, covered, total in rows:
        print(f"| {name} | {covered} / {total} | {percent(covered, total)} |")


if __name__ == "__main__":
    main(sys.argv[1:] or list(AREAS))
