#!/usr/bin/env python3
import os
import re
import sys

SKIPPED_DIRS = {".git", ".claude", ".audit", ".gradle", ".terraform", "node_modules", "build", "dist"}
HANGUL = re.compile(
    rb"\xe1[\x84-\x87][\x80-\xbf]"
    rb"|\xe3\x84[\xb0-\xbf]|\xe3\x85[\x80-\xbf]|\xe3\x86[\x80-\x8f]"
    rb"|\xea\xa5[\xa0-\xbf]"
    rb"|\xea[\xb0-\xbf][\x80-\xbf]|[\xeb\xec][\x80-\xbf]{2}|\xed[\x80-\x9f][\x80-\xbf]"
    rb"|\xef\xbe[\xa0-\xbf]|\xef\xbf[\x80-\x9c]"
)


def is_text(content):
    if b"\0" not in content:
        return True
    try:
        content.decode("utf-8")
    except UnicodeDecodeError:
        return False
    return True


root = sys.argv[1] if len(sys.argv) > 1 else "."
found = 0
for folder, dirs, files in os.walk(root):
    dirs[:] = [d for d in dirs if d not in SKIPPED_DIRS]
    for name in files:
        path = os.path.join(folder, name)
        try:
            with open(path, "rb") as handle:
                content = handle.read()
        except OSError:
            continue
        if not is_text(content):
            continue
        lines = content.split(b"\n")
        for number, line in enumerate(lines, 1):
            if HANGUL.search(line):
                print(f"{os.path.relpath(path, root)}:{number}: Hangul text")
                found += 1
sys.exit(1 if found else 0)
