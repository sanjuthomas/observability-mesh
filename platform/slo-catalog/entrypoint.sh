#!/bin/sh
# Starts embedded PostgreSQL, then slo-author-service and slo-provisioner-service.
set -eu

: "${POSTGRES_USER:=open_slo}"
: "${POSTGRES_PASSWORD:=open_slo}"
: "${POSTGRES_DB:=open_slo}"
: "${SPRING_DATASOURCE_URL:=jdbc:postgresql://127.0.0.1:5432/${POSTGRES_DB}}"
: "${SPRING_DATASOURCE_USERNAME:=${POSTGRES_USER}}"
: "${SPRING_DATASOURCE_PASSWORD:=${POSTGRES_PASSWORD}}"
: "${OBSERVABILITY_MESH_AUTH_ENABLED:=false}"
: "${PROMETHEUS_RULES_DIR:=/rules}"
: "${SLO_PROVISIONER_WORK_DIR:=/work}"
: "${SLOTH_BINARY:=/usr/local/bin/sloth}"
: "${PROMETHEUS_RELOAD_URL:=http://prometheus:9090/-/reload}"
: "${OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES:=prometheus}"

export POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB
export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
export OBSERVABILITY_MESH_AUTH_ENABLED
export PROMETHEUS_RULES_DIR SLO_PROVISIONER_WORK_DIR SLOTH_BINARY PROMETHEUS_RELOAD_URL
export OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES

mkdir -p "${PROMETHEUS_RULES_DIR}" "${SLO_PROVISIONER_WORK_DIR}"

log() { printf '[slo-catalog] %s\n' "$*"; }

shutdown() {
  log "shutting down"
  if [ -n "${AUTHOR_PID:-}" ]; then kill "${AUTHOR_PID}" 2>/dev/null || true; fi
  if [ -n "${PROVISIONER_PID:-}" ]; then kill "${PROVISIONER_PID}" 2>/dev/null || true; fi
  if [ -n "${PG_PID:-}" ]; then kill "${PG_PID}" 2>/dev/null || true; fi
  wait 2>/dev/null || true
  exit 0
}
trap shutdown INT TERM

log "starting PostgreSQL"
docker-entrypoint.sh postgres &
PG_PID=$!

log "waiting for PostgreSQL"
i=0
until pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "${i}" -gt 60 ]; then
    log "PostgreSQL did not become ready in time"
    exit 1
  fi
  sleep 1
done
log "PostgreSQL is ready"

log "starting slo-author-service on :9090"
OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME_AUTHOR:-slo-author-service}" \
  java -jar /app/slo-author-service.jar &
AUTHOR_PID=$!

log "starting slo-provisioner-service on :9097"
OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME_PROVISIONER:-slo-provisioner-service}" \
  java -jar /app/slo-provisioner-service.jar &
PROVISIONER_PID=$!

log "slo-catalog bundle is up (postgres=:5432 author=:9090 provisioner=:9097)"

# Exit if any child exits
while kill -0 "${PG_PID}" 2>/dev/null \
  && kill -0 "${AUTHOR_PID}" 2>/dev/null \
  && kill -0 "${PROVISIONER_PID}" 2>/dev/null; do
  sleep 2
done

log "a process exited; shutting down"
if [ -n "${AUTHOR_PID:-}" ]; then kill "${AUTHOR_PID}" 2>/dev/null || true; fi
if [ -n "${PROVISIONER_PID:-}" ]; then kill "${PROVISIONER_PID}" 2>/dev/null || true; fi
if [ -n "${PG_PID:-}" ]; then kill "${PG_PID}" 2>/dev/null || true; fi
wait 2>/dev/null || true
exit 1
