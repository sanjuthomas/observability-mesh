#!/bin/sh
set -eu

TEMPLATE=/etc/alertmanager/alertmanager.yml.template
CONFIG=/tmp/alertmanager.yml

: "${ALERTMANAGER_SMTP_HOST:=localhost}"
: "${ALERTMANAGER_SMTP_PORT:=587}"
: "${ALERTMANAGER_SMTP_FROM:=alerts@observability-mesh.local}"
: "${ALERTMANAGER_SMTP_USER:=}"
: "${ALERTMANAGER_SMTP_PASSWORD:=}"
: "${ALERTMANAGER_SMTP_REQUIRE_TLS:=true}"
: "${ALERTMANAGER_EMAIL_TO:=you@example.com}"

sed \
  -e "s|\${ALERTMANAGER_SMTP_HOST}|${ALERTMANAGER_SMTP_HOST}|g" \
  -e "s|\${ALERTMANAGER_SMTP_PORT}|${ALERTMANAGER_SMTP_PORT}|g" \
  -e "s|\${ALERTMANAGER_SMTP_FROM}|${ALERTMANAGER_SMTP_FROM}|g" \
  -e "s|\${ALERTMANAGER_SMTP_USER}|${ALERTMANAGER_SMTP_USER}|g" \
  -e "s|\${ALERTMANAGER_SMTP_PASSWORD}|${ALERTMANAGER_SMTP_PASSWORD}|g" \
  -e "s|\${ALERTMANAGER_SMTP_REQUIRE_TLS}|${ALERTMANAGER_SMTP_REQUIRE_TLS}|g" \
  -e "s|\${ALERTMANAGER_EMAIL_TO}|${ALERTMANAGER_EMAIL_TO}|g" \
  "${TEMPLATE}" > "${CONFIG}"

exec /bin/alertmanager --config.file="${CONFIG}" --storage.path=/alertmanager "$@"
