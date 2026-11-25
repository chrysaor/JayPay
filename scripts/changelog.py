#!/usr/bin/env python3
import datetime
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

TITLE = "# Changelog"
UNRELEASED = "## [Unreleased]"
VERSION_HEADING = re.compile(r"## \[(\d+\.\d+\.\d+)\] - (\d{4}-\d{2}-\d{2})$")
GROUPS = ("Added", "Changed", "Fixed", "Security")
REFERENCES = re.compile(r" \((\[#\d+\]\([^)\s]+\)(?:, \[#\d+\]\([^)\s]+\))*)\)$")
REFERENCE = re.compile(r"\[#(\d+)\]\(https://github\.com/chrysaor/JayPay/(?:pull|issues)/(\d+)\)$")
NO_ENTRY_TRAILER = re.compile(r"^Changelog: none$", re.M)


@dataclass
class Section:
    line: int
    version: str | None
    date: str | None
    groups: dict[str, list[str]] = field(default_factory=dict)

    @property
    def name(self):
        return f"[{self.version}]" if self.version else "Unreleased"

    @property
    def entries(self):
        return [entry for entries in self.groups.values() for entry in entries]


def version_key(version):
    return tuple(int(part) for part in version.split("."))


def parse(text, name):
    lines = text.splitlines()
    errors = []
    sections = []
    group = None

    def error(number, message):
        errors.append(f"{name}:{number}: {message}")

    if not lines or lines[0] != TITLE:
        error(1, f"the first line is not `{TITLE}`")
    for number, line in enumerate(lines, 1):
        if line.startswith("## "):
            group = None
            heading = VERSION_HEADING.match(line)
            if line == UNRELEASED:
                if sections:
                    error(number, "Unreleased is not the first section")
                sections.append(Section(number, None, None))
            elif heading:
                version, date = heading.groups()
                try:
                    datetime.date.fromisoformat(date)
                except ValueError:
                    error(number, f"{date} is not a date")
                if not sections:
                    error(number, "the first section is not `## [Unreleased]`")
                sections.append(Section(number, version, date))
            else:
                error(number, f"`{line}` is not a version heading `## [X.Y.Z] - YYYY-MM-DD`")
            continue
        if not sections or not line.strip():
            continue
        section = sections[-1]
        if line.startswith("### "):
            group = line[4:]
            if group not in GROUPS:
                error(number, f"### {group} is not one of {', '.join(GROUPS)}")
            elif group in section.groups or any(GROUPS.index(seen) > GROUPS.index(group) for seen in section.groups if seen in GROUPS):
                error(number, f"### {group} is out of the order {', '.join(GROUPS)} or repeated in {section.name}")
            section.groups.setdefault(group, [])
        elif line.startswith("- ") and group is not None:
            section.groups[group].append(line)
            references = REFERENCES.search(line)
            if not references:
                error(number, "the entry does not end with a PR or issue link such as ([#12](https://github.com/chrysaor/JayPay/pull/12))")
                continue
            for reference in references.group(1).split(", "):
                link = REFERENCE.match(reference)
                if not link or link.group(1) != link.group(2):
                    error(number, f"{reference} does not link to the same number on github.com/chrysaor/JayPay")
        else:
            error(number, "the line is neither a ### group nor a - entry under one")
    for section in sections:
        for group, entries in section.groups.items():
            if not entries:
                error(section.line, f"### {group} has no entries in {section.name}")
        if section.version and not section.groups:
            error(section.line, f"{section.name} has no entries")
    released = [section for section in sections if section.version]
    for newer, older in zip(released, released[1:]):
        if version_key(newer.version) <= version_key(older.version):
            error(newer.line, f"{newer.name} must be newer than {older.name} below it")
        if newer.date < older.date:
            error(newer.line, f"{newer.name} is dated before {older.name} below it")
    return sections, errors


def load(path):
    path = Path(path)
    sections, errors = parse(path.read_text(encoding="utf-8"), path.name)
    if errors:
        sys.exit("\n".join(errors))
    return path, sections


def check(path):
    load(path)


def notes(path, version):
    path, sections = load(path)
    section = next((section for section in sections if section.version == version), None)
    if section is None:
        sys.exit(f"{path.name}: no section for {version}")
    lines = path.read_text(encoding="utf-8").splitlines()
    end = next((later.line for later in sections if later.line > section.line), len(lines) + 1)
    print("\n".join(lines[section.line:end - 1]).strip())


def release(path, version, date):
    path, sections = load(path)
    if not sections or sections[0].version is not None or not sections[0].entries:
        sys.exit(f"{path.name}: Unreleased has no entries, so there is nothing to release as {version}")
    text = path.read_text(encoding="utf-8").replace(f"{UNRELEASED}\n", f"{UNRELEASED}\n\n## [{version}] - {date}\n", 1)
    _, errors = parse(text, path.name)
    if errors:
        sys.exit("\n".join(errors))
    path.write_text(text, encoding="utf-8")


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def entry(path, base):
    path, head = load(path)
    merge_base = git("merge-base", base, "HEAD").strip()
    try:
        base_sections, _ = parse(git("show", f"{merge_base}:{path.as_posix()}"), path.name)
    except subprocess.CalledProcessError:
        base_sections = []

    def unreleased(sections):
        return {entry for section in sections if section.version is None for entry in section.entries}

    def versions(sections):
        return {section.version for section in sections if section.version}

    if unreleased(head) - unreleased(base_sections) or versions(head) - versions(base_sections):
        return
    if NO_ENTRY_TRAILER.search(git("log", "--format=%B", f"{merge_base}..HEAD")):
        return
    sys.exit(
        f"{path.name}: this branch adds no line to Unreleased since {base}. "
        "Add one, or end a commit message with the trailer `Changelog: none` when the change needs no entry."
    )


COMMANDS = {"check": check, "notes": notes, "release": release, "entry": entry}

if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in COMMANDS:
        sys.exit("usage: changelog.py check FILE | notes FILE VERSION | release FILE VERSION DATE | entry FILE BASE")
    COMMANDS[sys.argv[1]](*sys.argv[2:])
