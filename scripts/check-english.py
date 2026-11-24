#!/usr/bin/env python3
import os
import re
import sys

SKIPPED_DIRS = {".git", ".claude", ".audit", ".gradle", ".terraform", "node_modules", "build", "dist"}
HANGUL = re.compile(rb"\xea[\xb0-\xbf][\x80-\xbf]|[\xeb\xec][\x80-\xbf]{2}|\xed[\x80-\x9e][\x80-\xbf]")

root = sys.argv[1] if len(sys.argv) > 1 else "."
found = 0
for folder, dirs, files in os.walk(root):
    dirs[:] = [d for d in dirs if d not in SKIPPED_DIRS]
    for name in files:
        path = os.path.join(folder, name)
        try:
            with open(path, "rb") as handle:
                lines = handle.read().split(b"\n")
        except OSError:
            continue
        for number, line in enumerate(lines, 1):
            if HANGUL.search(line):
                print(f"{os.path.relpath(path, root)}:{number}: Hangul text")
                found += 1
sys.exit(1 if found else 0)
