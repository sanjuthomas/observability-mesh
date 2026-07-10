#!/usr/bin/env bash
# Apply OpenSLO catalog seeds to the running Postgres (idempotent — ON CONFLICT DO NOTHING).
#
# Use when the stack is up but the catalog is empty or missing new seed rows, e.g. after
# adding documents to seed-slos.sql without recreating the postgres volume.
#
# Full tear-down + rebuild (recommended): docker compose down -v && docker compose up -d
#   — initdb runs postgres/init.sql then postgres/seed-slos.sql automatically.
#
# Usage (from repo root):
#   ./workloads/payment-ofac-demo/scripts/apply-open-slo-seed.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
SEED_FILE="${SCRIPT_DIR}/../postgres/seed-slos.sql"
COMPOSE_FILE="${REPO_ROOT}/workloads/payment-ofac-demo/docker-compose.yml"
ENV_FILE="${REPO_ROOT}/workloads/payment-ofac-demo/.env"

if [[ ! -f "${SEED_FILE}" ]]; then
  echo "Seed file not found: ${SEED_FILE}" >&2
  exit 1
fi

log() { printf '>>> %s\n' "$*"; }

compose() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" "$@"
}

log "Applying OpenSLO catalog seed from ${SEED_FILE}"
compose exec -T postgres psql -U open_slo -d open_slo -v ON_ERROR_STOP=1 < "${SEED_FILE}"

log "Catalog seed applied. Active documents:"
compose exec -T postgres psql -U open_slo -d open_slo -c \
  "SELECT kind, name FROM service_level_objectives WHERE stale = false ORDER BY kind, name;")

log "Wait ~60s for slo-provisioner-service to publish Prometheus rules."
