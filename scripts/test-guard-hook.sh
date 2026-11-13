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

[[ "$failures" == 0 ]] || { echo "${failures} check(s) failed"; exit 1; }
echo "all guard hook checks passed"
