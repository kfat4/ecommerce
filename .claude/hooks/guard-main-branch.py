#!/usr/bin/env python3
"""PreToolUse hook for Bash: only the owner changes `main` (CLAUDE.md, Git section).

Skills and plugins (for example superpowers' finishing-a-development-branch) may suggest committing or
merging into the base branch. This hook enforces the rule whatever the instruction source.

Blocks (exit code 2, message on stderr is shown to the model):
  * `git commit`, `git merge`, `git cherry-pick`, `git rebase` while `main` is checked out.
  * `git push` that targets `main` (explicit refspec, or a bare push while on `main`), `--all`, `--mirror`.
  * `gh pr merge`: the owner merges pull requests.

`git pull` on main stays allowed: branching from an up-to-date main needs it.
The command is split on && || ; | and newlines; `cd <dir>`, `git -C <dir>` and
`git switch/checkout main` earlier in the same command are taken into account.
"""
from __future__ import annotations  # macOS ships Python 3.9

import json
import os
import re
import shlex
import subprocess
import sys

PROTECTED = "main"
CHANGES_CURRENT_BRANCH = {"commit", "merge", "cherry-pick", "rebase"}
GIT_OPTIONS_WITH_VALUE = {"-C", "-c", "--git-dir", "--work-tree", "--namespace"}
SEGMENT_SEPARATOR = re.compile(r"&&|\|\||[;|\n]")


def current_branch(directory: str) -> str:
    result = subprocess.run(["git", "-C", directory, "symbolic-ref", "--short", "-q", "HEAD"],
                            capture_output=True, text=True)
    return result.stdout.strip()


def is_protected(ref: str) -> bool:
    return ref.lstrip("+").removeprefix("refs/heads/") == PROTECTED


def split_git(words: list[str], directory: str) -> tuple[str, list[str], str]:
    """Returns (subcommand, its arguments, directory) for `git [global options] <subcommand> ...`."""
    i = 1
    while i < len(words) and words[i].startswith("-"):
        if words[i] == "-C" and i + 1 < len(words):
            directory = os.path.join(directory, os.path.expanduser(words[i + 1]))
        i += 2 if words[i] in GIT_OPTIONS_WITH_VALUE else 1
    if i >= len(words):
        return "", [], directory
    return words[i], words[i + 1:], directory


def push_targets_main(args: list[str], branch: str) -> bool:
    if "--all" in args or "--mirror" in args:
        return True
    positional = [a for a in args if not a.startswith("-")]
    refspecs = positional[1:]  # the first positional argument is the remote
    if not refspecs:
        return branch == PROTECTED
    for refspec in refspecs:
        destination = refspec.split(":", 1)[-1]
        if destination == "HEAD":
            destination = branch
        if is_protected(destination):
            return True
    return False


def check(command: str, directory: str) -> str | None:
    """Returns the reason to block, or None."""
    branch_override = None  # set by `git switch/checkout main` earlier in the same command
    for segment in SEGMENT_SEPARATOR.split(command):
        try:
            words = shlex.split(segment)
        except ValueError:
            words = segment.split()
        while words and "=" in words[0] and not words[0].startswith("-"):
            words = words[1:]  # leading VAR=value assignments
        if words[:1] == ["env"]:
            words = words[1:]
        if not words:
            continue
        if words[0] == "cd" and len(words) > 1:
            directory = os.path.join(directory, os.path.expanduser(words[1]))
            branch_override = None
            continue
        if words[:3] == ["gh", "pr", "merge"]:
            return "only the owner merges pull requests"
        if words[0] != "git":
            continue

        subcommand, args, git_dir = split_git(words, directory)
        branch = branch_override or current_branch(git_dir)
        if subcommand in ("switch", "checkout"):
            targets = [a for a in args if not a.startswith("-")]
            branch_override = targets[0] if targets and "-b" not in args and "-c" not in args else None
        elif subcommand in CHANGES_CURRENT_BRANCH and branch == PROTECTED:
            return f"`git {subcommand}` while `{PROTECTED}` is checked out"
        elif subcommand == "push" and push_targets_main(args, branch):
            return f"`git push` that would update `{PROTECTED}`"
    return None


def main() -> int:
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return 0  # never break the session because of a hook bug
    command = (event.get("tool_input") or {}).get("command") or ""
    if "git" not in command and "gh" not in command:
        return 0
    directory = event.get("cwd") or os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    reason = check(command, directory)
    if reason:
        print(f"Blocked: {reason}. `{PROTECTED}` changes only through a pull request the owner merges. "
              "Work on a feat/ fix/ docs/ branch, merge it into develop to verify, then open a PR to main "
              "(CLAUDE.md, Git section).", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
