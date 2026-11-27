#!/usr/bin/env python3
import os
import re
import subprocess
import sys
from urllib.parse import quote, quote_plus

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMPOSE = ["docker", "compose", "-f", os.path.join(ROOT, "infra", "docker", "compose.yaml")]
ENV_FILE = os.path.join(ROOT, "infra", "docker", ".env")
PRINTED_SECRET = re.compile(r"(Unseal Key|Root Token): \S+")


def secrets():
    found = {}
    if not os.path.exists(ENV_FILE):
        return []
    with open(ENV_FILE, encoding="utf-8") as handle:
        for line in handle.read().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                for form in {value, quote(value, safe=""), quote_plus(value)}:
                    if form:
                        found[form] = key.strip()
    return sorted(found.items(), key=lambda item: len(item[0]), reverse=True)


def redact(text, known):
    text = PRINTED_SECRET.sub(lambda match: f"{match.group(1)}: <redacted>", text)
    for value, key in known:
        text = text.replace(value, f"<redacted {key}>")
    return text


def run(*args):
    result = subprocess.run([*COMPOSE, *args], capture_output=True, text=True, errors="replace")
    return result.stdout + result.stderr


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: scripts/stack-logs.py <directory>")
    target = sys.argv[1]
    os.makedirs(target, exist_ok=True)
    known = secrets()
    files = {"ps.txt": run("ps", "--all")}
    for service in run("ps", "--all", "--services").split():
        files[f"{service}.log"] = run("logs", "--no-color", "--timestamps", service)
    for name, text in files.items():
        with open(os.path.join(target, name), "w", encoding="utf-8") as handle:
            handle.write(redact(text, known))
    print(f"stack-logs: wrote {len(files)} files to {target}")


if __name__ == "__main__":
    main()
