#!/usr/bin/env bash
# Tests .claude/hooks/guard-protected-files.py: a guard nobody tests is a guard nobody can rely on.
set -euo pipefail
cd "$(dirname "$0")/.."
export CLAUDE_PROJECT_DIR="$PWD"
HOOK=.claude/hooks/guard-protected-files.py
failures=0

expect() {  # expect <exit code> <description> <file path relative to the repo>
  local expected="$1" description="$2" path="$3" actual=0
  printf '{"tool_name":"Edit","tool_input":{"file_path":"%s/%s"}}' "$PWD" "$path" \
    | python3 "$HOOK" 2>/dev/null || actual=$?
  if [[ "$actual" == "$expected" ]]; then
    echo "ok    ${description}"
  else
    echo "FAIL  ${description}: expected exit ${expected}, got ${actual}"; failures=$((failures + 1))
  fi
}

expect 2 "blocks .env"                         ".env"
expect 2 "blocks .env.production"              ".env.production"
expect 2 "blocks a private key"                "secrets/server.key"
expect 0 "allows .env.example"                 ".env.example"
expect 2 "blocks a committed Flyway migration" "services/order-service/src/main/resources/db/migration/V1__create_order_tables.sql"
expect 0 "allows a new Flyway migration"       "services/order-service/src/main/resources/db/migration/V999__not_yet_created.sql"
expect 0 "allows ordinary source files"        "pom.xml"

if ! echo 'not json' | python3 "$HOOK"; then
  echo "FAIL  malformed input must not block the session"; failures=$((failures + 1))
else
  echo "ok    ignores malformed input"
fi

# .claude/hooks/guard-main-branch.py, against a throwaway repo so the result does not depend on this checkout.
MAIN_HOOK="$PWD/.claude/hooks/guard-main-branch.py"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
git -C "$repo" init -q -b main
git -C "$repo" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init
git -C "$repo" branch feat/x

expect_bash() {  # expect_bash <exit code> <description> <checked-out branch> <command>
  local expected="$1" description="$2" branch="$3" command="$4" actual=0
  git -C "$repo" switch -q "$branch"
  python3 -c 'import json,sys; print(json.dumps({"tool_name":"Bash","cwd":sys.argv[1],"tool_input":{"command":sys.argv[2]}}))' \
    "$repo" "$command" | python3 "$MAIN_HOOK" 2>/dev/null || actual=$?
  if [[ "$actual" == "$expected" ]]; then
    echo "ok    ${description}"
  else
    echo "FAIL  ${description}: expected exit ${expected}, got ${actual}"; failures=$((failures + 1))
  fi
}

expect_bash 2 "blocks commit on main"                 main   'git commit -m x'
expect_bash 2 "blocks merge on main"                  main   'git merge feat/x'
expect_bash 2 "blocks cd elsewhere + git -C commit"   main   "cd / && git -C $repo commit -m x"
expect_bash 0 "allows git -C commit on a feature br." feat/x "cd / && git -C $repo commit -m x"
expect_bash 2 "blocks switch to main then merge"      feat/x 'git switch main && git merge feat/x'
expect_bash 2 "blocks push to main"                   feat/x 'git push origin HEAD:main'
expect_bash 2 "blocks push of refs/heads/main"        feat/x 'git push origin +refs/heads/main'
expect_bash 2 "blocks bare push while on main"        main   'git push'
expect_bash 2 "blocks push --all"                     feat/x 'git push --all origin'
expect_bash 2 "blocks gh pr merge"                    feat/x 'gh pr merge 12 --squash'
expect_bash 0 "allows pull on main"                   main   'git pull --ff-only'
expect_bash 0 "allows branching off main"             main   'git switch -c feat/y'
expect_bash 0 "allows commit on a feature branch"     feat/x 'git commit -m x'
expect_bash 0 "allows merge into develop"             feat/x 'git switch develop && git merge feat/x'
expect_bash 0 "allows pushing a feature branch"       feat/x 'git push -u origin feat/x'
expect_bash 0 "allows creating a PR"                  feat/x 'gh pr create --base main'
expect_bash 0 "allows unrelated commands"             main   'ls -la'

[[ "$failures" == 0 ]] || { echo "${failures} check(s) failed"; exit 1; }
echo "all guard hook checks passed"
