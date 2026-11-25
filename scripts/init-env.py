#!/usr/bin/env python3
import os
import re
import secrets
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXAMPLE = os.path.join(ROOT, "infra", "docker", ".env.example")
PLACEHOLDER = re.compile(r"^<hex:(\d+)>$")


def read_env(path):
    entries = []
    if not os.path.exists(path):
        return entries
    with open(path, encoding="utf-8") as handle:
        for line in handle.read().splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                entries.append((key.strip(), value))
    return entries


def is_set(value):
    return value != "" and not (value.startswith("<") and value.endswith(">"))


def main():
    target = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "infra", "docker", ".env")
    current = dict(read_env(target))
    example = read_env(EXAMPLE)
    lines = []
    for key, example_value in example:
        value = current.get(key, "")
        if is_set(value):
            lines.append(f"{key}={value}")
            continue
        placeholder = PLACEHOLDER.match(example_value)
        if placeholder:
            value = secrets.token_hex(int(placeholder.group(1)))
            print(f"init-env: generated {key}")
        else:
            value = example_value
            print(f"init-env: copied {key} from .env.example")
        lines.append(f"{key}={value}")
    known = {key for key, _ in example}
    lines.extend(f"{key}={value}" for key, value in current.items() if key not in known)

    descriptor = os.open(target + ".tmp", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    os.replace(target + ".tmp", target)
    os.chmod(target, 0o600)
    print(f"init-env: {os.path.relpath(target, ROOT)} is ready")


if __name__ == "__main__":
    main()
