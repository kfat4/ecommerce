#!/usr/bin/env python3
"""PreToolUse hook for Edit/Write: instructions in CLAUDE.md are advice, a hook is enforcement.

Blocks (exit code 2, message on stderr is shown to the model):
  * .env files (except .env.example) and key/certificate material: secrets never go through the agent.
  * Flyway migrations that are already committed: applied migrations are immutable, add V<n+1> instead.
"""
import json
import os
import re
import subprocess
import sys

SECRET_FILE = re.compile(r"(^|/)\.env(\..+)?$|\.(pem|key|p12|jks|keystore|pfx)$")
MIGRATION = re.compile(r"/db/migration/V[0-9][^/]*\.sql$")


def tracked_by_git(path: str, project_dir: str) -> bool:
    result = subprocess.run(["git", "-C", project_dir, "ls-files", "--error-unmatch", path],
                            capture_output=True)
    return result.returncode == 0


def main() -> int:
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return 0  # not our business; never break the session because of a hook bug
    path = (event.get("tool_input") or {}).get("file_path") or ""
    if not path:
        return 0
    project_dir = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    normalized = path.replace("\\", "/")

    if SECRET_FILE.search(normalized) and not normalized.endswith(".env.example"):
        print(f"Blocked: {os.path.basename(path)} may hold secrets. Ask the user to edit it themselves; "
              "put placeholders in .env.example instead.", file=sys.stderr)
        return 2

    if MIGRATION.search(normalized) and os.path.exists(path) and tracked_by_git(path, project_dir):
        print(f"Blocked: {os.path.basename(path)} is a committed Flyway migration and must stay immutable. "
              "Create the next V<n>__description.sql instead (see .claude/rules/database-migrations.md).",
              file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
