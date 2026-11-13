#!/usr/bin/env bash
# =================================================================================================
#  End-to-end smoke test against a running stack (docker compose up). Talks only to the API gateway,
#  exactly like a real client, and proves the whole chain works:
#
#    register + login (JWT)  ->  catalogue (MongoDB)  ->  place order (Idempotency-Key)
#    ->  saga over Kafka (inventory, payment)  ->  final order state  ->  search read model (Elasticsearch)
#
#  Scenarios (see the demo data in CatalogSeeder and V3__seed_demo_stock.sql):
#    1. happy path                  PRD-1003 x2            -> CONFIRMED
#    2. same Idempotency-Key again                         -> same order, Idempotent-Replayed: true
#    3. not enough stock            PRD-1004 x4 (3 left)   -> CANCELLED, no compensation needed
#    4. payment declined            PRD-1005 x1 (> 10 000) -> CANCELLED after releasing the stock
#    5. search finds the catalogue  q=laptop               -> PRD-1001
#
#  Usage: scripts/smoke-test.sh [base-url]      (default http://localhost:8080)
#  Needs: bash, curl, python3. Creates one throw-away user per run.
# =================================================================================================
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://localhost:8080}}"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-300}"   # seconds to wait for the stack to become ready
SAGA_TIMEOUT="${SAGA_TIMEOUT:-90}"          # seconds to wait for an order to reach a final state

RUN_ID="$(date +%s)-$RANDOM"
EMAIL="smoke-${RUN_ID}@example.com"
PASSWORD="Smoke-${RUN_ID}-pw"               # throw-away test credential, generated per run
BODY_FILE="$(mktemp)"
HEADER_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE" "$HEADER_FILE"' EXIT

PASSED=0
log()  { printf '%s\n' "$*"; }
pass() { PASSED=$((PASSED + 1)); log "  ✓ $*"; }
fail() { log "  ✗ $*"; log "    last response body: $(head -c 600 "$BODY_FILE")"; exit 1; }

# json <python-expression over d>   e.g. json 'd["status"]'
json() { python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(eval(sys.argv[2]))' "$BODY_FILE" "$1"; }

# request METHOD PATH [curl args...]  -> prints the HTTP status, body in $BODY_FILE, headers in $HEADER_FILE
request() {
  local method="$1" path="$2"; shift 2
  curl -sS -o "$BODY_FILE" -D "$HEADER_FILE" -w '%{http_code}' -X "$method" "${BASE_URL}${path}" \
       -H 'Accept: application/json' "$@" || true   # curl already printed 000 for "no connection"
}

wait_until() {  # wait_until <seconds> <description> <command...>
  local timeout="$1" what="$2"; shift 2
  local deadline=$((SECONDS + timeout))
  until "$@"; do
    if (( SECONDS >= deadline )); then fail "timed out after ${timeout}s waiting for: ${what}"; fi
    sleep 3
  done
}

gateway_up()      { [[ "$(request GET /actuator/health)" == "200" ]]; }
catalogue_ready() { [[ "$(request GET /api/v1/products/PRD-1003)" == "200" ]]; }
can_register()    {
  local status
  status="$(request POST /api/v1/auth/register -H 'Content-Type: application/json' \
            -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"fullName\":\"Smoke Test\"}")"
  # 422 = e-mail already registered: an earlier attempt of this run got through although its response was lost
  [[ "$status" == "201" || "$status" == "422" ]]
}

place_order() {  # place_order <idempotency-key> <productId> <quantity>
  request POST /api/v1/orders -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
          -H "Idempotency-Key: $1" -d "{\"items\":[{\"productId\":\"$2\",\"quantity\":$3}]}"
}

order_is_final() {  # order_is_final <orderId>
  [[ "$(request GET "/api/v1/orders/$1" -H "Authorization: Bearer ${TOKEN}")" == "200" ]] || return 1
  ORDER_STATUS="$(json 'd["status"]')"
  [[ "$ORDER_STATUS" == "CONFIRMED" || "$ORDER_STATUS" == "CANCELLED" ]]
}

expect_order() {  # expect_order <description> <productId> <quantity> <expected final status>
  local description="$1" product="$2" quantity="$3" expected="$4"
  local key="smoke-${RUN_ID}-${product}"
  local status; status="$(place_order "$key" "$product" "$quantity")"
  [[ "$status" == "202" ]] || fail "${description}: expected 202 Accepted, got ${status}"
  local order_id; order_id="$(json 'd["id"]')"
  wait_until "$SAGA_TIMEOUT" "order ${order_id} to reach a final state" order_is_final "$order_id"
  [[ "$ORDER_STATUS" == "$expected" ]] \
    || fail "${description}: expected ${expected}, got ${ORDER_STATUS} (reason: $(json 'd.get("failureReason")'))"
  pass "${description}: ${ORDER_STATUS}$( [[ "$expected" == "CANCELLED" ]] && printf ' (%s)' "$(json 'd.get("failureReason")')" )"
  LAST_ORDER_ID="$order_id"; LAST_KEY="$key"; LAST_PRODUCT="$product"; LAST_QUANTITY="$quantity"
}

search_finds_laptop() {
  [[ "$(request GET '/api/v1/search/products?q=laptop')" == "200" ]] || return 1
  [[ "$(json 'any(i["id"] == "PRD-1001" for i in d["items"])')" == "True" ]]
}

# -------------------------------------------------------------------------------------------------
log "Smoke test against ${BASE_URL}"

log "1. Waiting for the stack (up to ${STARTUP_TIMEOUT}s)"
wait_until "$STARTUP_TIMEOUT" "gateway health" gateway_up
pass "gateway is up"
wait_until "$STARTUP_TIMEOUT" "product-service behind the gateway (catalogue seeded)" catalogue_ready
pass "catalogue is served through the gateway"

log "2. Authentication"
wait_until "$STARTUP_TIMEOUT" "auth-service to accept a registration" can_register
pass "registered a throw-away user"
status="$(request POST /api/v1/auth/login -H 'Content-Type: application/json' \
          -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")"
[[ "$status" == "200" ]] || fail "login: expected 200, got ${status}"
TOKEN="$(json 'd["accessToken"]')"
pass "logged in and received a JWT"
status="$(request GET /api/v1/orders)"
[[ "$status" == "401" ]] || fail "orders without a token: expected 401, got ${status}"
pass "protected endpoint rejects a request without a token (401)"

log "3. Order saga"
# The first order also waits for every saga participant to have joined its consumer group.
SAGA_TIMEOUT_BACKUP="$SAGA_TIMEOUT"; SAGA_TIMEOUT="$STARTUP_TIMEOUT"
expect_order "happy path (PRD-1003 x2)" PRD-1003 2 CONFIRMED
SAGA_TIMEOUT="$SAGA_TIMEOUT_BACKUP"

status="$(place_order "$LAST_KEY" "$LAST_PRODUCT" "$LAST_QUANTITY")"
[[ "$status" == "202" || "$status" == "200" ]] || fail "idempotent retry: expected the stored response, got ${status}"
[[ "$(json 'd["id"]')" == "$LAST_ORDER_ID" ]] || fail "idempotent retry created a second order"
grep -qi '^Idempotent-Replayed: *true' "$HEADER_FILE" || fail "idempotent retry: Idempotent-Replayed header missing"
pass "retry with the same Idempotency-Key returns the same order (replayed, not executed twice)"

status="$(request POST /api/v1/orders -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
          -d '{"items":[{"productId":"PRD-1003","quantity":1}]}')"
[[ "$status" == "400" ]] || fail "order without Idempotency-Key: expected 400, got ${status}"
pass "order without Idempotency-Key is rejected (400)"

expect_order "insufficient stock (PRD-1004 x4, 3 in stock)" PRD-1004 4 CANCELLED
expect_order "payment declined (PRD-1005 x1, amount > 10 000)" PRD-1005 1 CANCELLED

log "4. Search read model"
wait_until "$SAGA_TIMEOUT" "product.events to be projected into Elasticsearch" search_finds_laptop
pass "search for 'laptop' finds PRD-1001 (event -> Kafka -> Elasticsearch)"

log ""
log "All ${PASSED} checks passed."
