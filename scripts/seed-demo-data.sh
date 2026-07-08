#!/usr/bin/env bash
# Seed the Observability Mesh demo stack with instructions, payments, and ALERT security events.
#
# Usage (from repo root):
#   ./scripts/seed-demo-data.sh
#   ./scripts/seed-demo-data.sh --seed-only
#   ./scripts/seed-demo-data.sh --full-reset
#
# Environment overrides (optional):
#   HARNESS_URL=http://localhost:9091
#   ADMIN_USER=admin-001
#   ADMIN_PASSWORD=Password1!
#   KEYCLOAK_WAIT_SECONDS=45
#   COMPOSE_UP_BUILD=1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

HARNESS_URL="${HARNESS_URL:-http://localhost:9091}"
ADMIN_USER="${ADMIN_USER:-admin-001}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Password1!}"

CREATE_INSTRUCTIONS="${CREATE_INSTRUCTIONS:-18}"
APPROVE_INSTRUCTIONS="${APPROVE_INSTRUCTIONS:-12}"
SUBMIT_INSTRUCTIONS="${SUBMIT_INSTRUCTIONS:-4}"
REJECT_INSTRUCTIONS="${REJECT_INSTRUCTIONS:-2}"
INSTRUCTION_POLICY_RUNS="${INSTRUCTION_POLICY_RUNS:-12}"

CREATE_PAYMENTS="${CREATE_PAYMENTS:-12}"
SUBMIT_PAYMENTS="${SUBMIT_PAYMENTS:-8}"
APPROVE_PAYMENTS="${APPROVE_PAYMENTS:-5}"
REJECT_PAYMENTS="${REJECT_PAYMENTS:-2}"
PAYMENT_POLICY_RUNS="${PAYMENT_POLICY_RUNS:-10}"

KEYCLOAK_WAIT_SECONDS="${KEYCLOAK_WAIT_SECONDS:-45}"
COMPOSE_UP_BUILD="${COMPOSE_UP_BUILD:-1}"

DO_FULL_RESET=1

usage() {
  sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'
  echo
  echo "Options:"
  echo "  --full-reset   Stop stack, remove volumes, rebuild, re-seed Keycloak (default)"
  echo "  --seed-only    Skip reset; only run harness seed actions (stack must be up)"
  echo "  -h, --help     Show this help"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --full-reset) DO_FULL_RESET=1; shift ;;
    --seed-only) DO_FULL_RESET=0; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

log() { printf '\n>>> %s\n' "$*"; }

summarize_json() {
  python3 -c '
import json, sys
d = json.load(sys.stdin)
print("  ok=%s succeeded=%s failed=%s" % (d.get("ok"), d.get("succeeded"), d.get("failed", 0)))
'
}

reset_stack() {
  log "Stopping stack and removing volumes"
  (cd "${REPO_ROOT}" && docker compose down -v --remove-orphans)

  log "Starting stack"
  if [[ "${COMPOSE_UP_BUILD}" == "1" ]]; then
    (cd "${REPO_ROOT}" && docker compose up -d --build)
  else
    (cd "${REPO_ROOT}" && docker compose up -d)
  fi

  log "Waiting ${KEYCLOAK_WAIT_SECONDS}s for Keycloak and services"
  sleep "${KEYCLOAK_WAIT_SECONDS}"
}

harness_login() {
  LOGIN_JSON="$(curl -sf -X POST "${HARNESS_URL}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"userId\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASSWORD}\"}")"
  HARNESS_TOKEN="$(printf '%s' "${LOGIN_JSON}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["session_token"])')"
  HARNESS_SESSION_ID="$(printf '%s' "${LOGIN_JSON}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["session_id"])')"
  export HARNESS_TOKEN HARNESS_SESSION_ID
}

harness_action() {
  local action="$1"
  local count="${2:-}"
  log "Harness action: ${action}${count:+ (count=${count})}"
  if [[ -n "${count}" ]]; then
    curl -sf -X POST "${HARNESS_URL}/api/actions/${action}" \
      -H "Authorization: Bearer ${HARNESS_TOKEN}" \
      -H "X-Session-Id: ${HARNESS_SESSION_ID}" \
      -H 'Content-Type: application/json' \
      -d "{\"count\":${count}}" | summarize_json
  else
    curl -sf -X POST "${HARNESS_URL}/api/actions/${action}" \
      -H "Authorization: Bearer ${HARNESS_TOKEN}" \
      -H "X-Session-Id: ${HARNESS_SESSION_ID}" | summarize_json
  fi
}

harness_scenario_loop() {
  local action="$1"
  local runs="$2"
  log "Harness scenario: ${action} x${runs}"
  local i ok=0 fail=0
  for i in $(seq 1 "${runs}"); do
    if curl -sf -X POST "${HARNESS_URL}/api/actions/${action}" \
      -H "Authorization: Bearer ${HARNESS_TOKEN}" \
      -H "X-Session-Id: ${HARNESS_SESSION_ID}" \
      | python3 -c 'import sys,json; sys.exit(0 if json.load(sys.stdin).get("ok") else 1)'; then
      ok=$((ok + 1))
    else
      fail=$((fail + 1))
      echo "  run ${i}: completed with expected step failure(s)"
    fi
  done
  echo "  finished: ${ok} fully passed, ${fail} with step failure(s)"
}

print_mongo_alert_counts() {
  log "MongoDB security event ALERT counts"
  docker exec mongodb mongosh --quiet security_events --eval '
const inst = db["instruction_service"];
const pay = db["payment_service"];
printjson({
  instruction_ALERT: inst.countDocuments({severity:"ALERT"}),
  instruction_INFO: inst.countDocuments({severity:"INFO"}),
  payment_ALERT: pay.countDocuments({severity:"ALERT"}),
  payment_INFO: pay.countDocuments({severity:"INFO"}),
});
'
}

print_harness_status() {
  log "Harness status"
  curl -sf "${HARNESS_URL}/api/status" \
    -H "Authorization: Bearer ${HARNESS_TOKEN}" \
    -H "X-Session-Id: ${HARNESS_SESSION_ID}" \
    | python3 -m json.tool
}

run_seed() {
  log "Logging in to harness as ${ADMIN_USER}"
  harness_login

  log "Base instruction lifecycle"
  harness_action create-instructions "${CREATE_INSTRUCTIONS}"
  harness_action approve-instructions "${APPROVE_INSTRUCTIONS}"
  harness_action submit-instructions "${SUBMIT_INSTRUCTIONS}"
  harness_action reject-instructions "${REJECT_INSTRUCTIONS}"
  harness_scenario_loop run-policy-scenario "${INSTRUCTION_POLICY_RUNS}"

  log "Base payment lifecycle"
  harness_action create-payments "${CREATE_PAYMENTS}"
  harness_action submit-payments "${SUBMIT_PAYMENTS}"
  harness_action approve-payments "${APPROVE_PAYMENTS}"
  harness_action reject-payments "${REJECT_PAYMENTS}"
  harness_scenario_loop run-payment-policy-scenario "${PAYMENT_POLICY_RUNS}"

  print_mongo_alert_counts
  print_harness_status

  log "Done — open http://localhost:9091 for harness status"
  log "Instruction browser: http://localhost:9000/ui/"
  log "Payment browser: http://localhost:9093/ui/"
}

main() {
  if [[ "${DO_FULL_RESET}" == "1" ]]; then
    reset_stack
  fi
  run_seed
}

main "$@"
