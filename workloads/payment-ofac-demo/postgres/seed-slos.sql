-- payment-ofac-demo OpenSLO catalog seed (Postgres initdb only — runs on first volume create).
-- Mounted as docker-entrypoint-initdb.d/02-seed-slos.sql (see docker-compose.yml).
--
-- Documents (version 1, stale=false):
--   SLI  sanction-scan-completion
--   SLO  sanction-scan-completion-30d
--   SLI  sanction-scan-under-one-minute
--   SLO  sanction-scan-latency-30d
--   SLI  payment-approval-security-sli
--   AlertCondition  payment-approval-security-condition
--   AlertNotificationTarget  observability-mesh-email
--   AlertPolicy  payment-approval-security-alert
--
-- metricSourceRef payment-prometheus must match OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES.

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/SLI/sanction-scan-completion',
    1,
    false,
    'openslo/v1',
    'SLI',
    'sanction-scan-completion',
    '{
      "apiVersion": "openslo/v1",
      "kind": "SLI",
      "metadata": {
        "name": "sanction-scan-completion",
        "displayName": "Sanction scan completion"
      },
      "spec": {
        "description": "Ratio of sanction scans that reach a definitive result (PASSED or FAILED).",
        "ratioMetric": {
          "good": {
            "metricSource": {
              "metricSourceRef": "payment-prometheus",
              "spec": {
                "query": "sum(increase(sanction_scan_completed_total{result=~\"PASSED|FAILED\"}[5m]))"
              }
            }
          },
          "total": {
            "metricSource": {
              "metricSourceRef": "payment-prometheus",
              "spec": {
                "query": "sum(increase(sanction_scan_completed_total[5m]))"
              }
            }
          }
        }
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/SLO/sanction-scan-completion-30d',
    1,
    false,
    'openslo/v1',
    'SLO',
    'sanction-scan-completion-30d',
    '{
      "apiVersion": "openslo/v1",
      "kind": "SLO",
      "metadata": {
        "name": "sanction-scan-completion-30d",
        "displayName": "Sanction scan completion (30-day rolling)"
      },
      "spec": {
        "service": "payment-platform",
        "description": "99% of sanction scans complete with PASSED or FAILED; up to 1% may be UNABLE_TO_DETERMINE.",
        "indicatorRef": "sanction-scan-completion",
        "timeWindow": [
          {
            "duration": "30d",
            "isRolling": true
          }
        ],
        "budgetingMethod": "Occurrences",
        "objectives": [
          {
            "displayName": "Completion target",
            "target": 0.99
          }
        ]
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/SLI/sanction-scan-under-one-minute',
    1,
    false,
    'openslo/v1',
    'SLI',
    'sanction-scan-under-one-minute',
    '{
      "apiVersion": "openslo/v1",
      "kind": "SLI",
      "metadata": {
        "name": "sanction-scan-under-one-minute",
        "displayName": "Sanction scan under one minute"
      },
      "spec": {
        "description": "Ratio of definitive sanction scans (PASSED or FAILED) completed within 60 seconds.",
        "ratioMetric": {
          "good": {
            "metricSource": {
              "metricSourceRef": "payment-prometheus",
              "spec": {
                "query": "sum(increase(sanction_scan_completed_total{duration_le=\"60s\",result=~\"PASSED|FAILED\"}[5m]))"
              }
            }
          },
          "total": {
            "metricSource": {
              "metricSourceRef": "payment-prometheus",
              "spec": {
                "query": "sum(increase(sanction_scan_completed_total{result=~\"PASSED|FAILED\"}[5m]))"
              }
            }
          }
        }
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/SLO/sanction-scan-latency-30d',
    1,
    false,
    'openslo/v1',
    'SLO',
    'sanction-scan-latency-30d',
    '{
      "apiVersion": "openslo/v1",
      "kind": "SLO",
      "metadata": {
        "name": "sanction-scan-latency-30d",
        "displayName": "Sanction scan latency (30-day rolling)"
      },
      "spec": {
        "service": "payment-platform",
        "description": "99% of definitive sanction scans complete within one minute; 1% error budget for slower completions.",
        "indicatorRef": "sanction-scan-under-one-minute",
        "timeWindow": [
          {
            "duration": "30d",
            "isRolling": true
          }
        ],
        "budgetingMethod": "Occurrences",
        "objectives": [
          {
            "displayName": "Latency target",
            "target": 0.99
          }
        ]
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/SLI/payment-approval-security-sli',
    1,
    false,
    'openslo/v1',
    'SLI',
    'payment-approval-security-sli',
    '{
      "apiVersion": "openslo/v1",
      "kind": "SLI",
      "metadata": {
        "name": "payment-approval-security-sli",
        "displayName": "Payment approval security events"
      },
      "spec": {
        "description": "Count of payment APPROVE attempts denied with ALERT severity.",
        "thresholdMetric": {
          "metricSource": {
            "metricSourceRef": "payment-prometheus",
            "spec": {
              "query": "sum(increase(payment_security_events_total{action=\"APPROVE\",severity=\"ALERT\"}[5m]))"
            }
          }
        }
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/AlertCondition/payment-approval-security-condition',
    1,
    false,
    'openslo/v1',
    'AlertCondition',
    'payment-approval-security-condition',
    '{
      "apiVersion": "openslo/v1",
      "kind": "AlertCondition",
      "metadata": {
        "name": "payment-approval-security-condition",
        "displayName": "Payment approval security ALERT",
        "annotations": {
          "observability-mesh.alert-type": "metric-threshold",
          "observability-mesh.sli-ref": "payment-approval-security-sli"
        }
      },
      "spec": {
        "description": "Fire when any payment APPROVE attempt is denied with ALERT severity.",
        "severity": "page",
        "condition": {
          "kind": "burnrate",
          "op": "gt",
          "threshold": 0,
          "lookbackWindow": "5m",
          "alertAfter": "0m"
        }
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/AlertNotificationTarget/observability-mesh-email',
    1,
    false,
    'openslo/v1',
    'AlertNotificationTarget',
    'observability-mesh-email',
    '{
      "apiVersion": "openslo/v1",
      "kind": "AlertNotificationTarget",
      "metadata": {
        "name": "observability-mesh-email",
        "displayName": "Observability Mesh email"
      },
      "spec": {
        "description": "Tenant email route via Alertmanager (observabilitymesh@sanju.org in demo).",
        "target": "email"
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/AlertPolicy/payment-approval-security-alert',
    1,
    false,
    'openslo/v1',
    'AlertPolicy',
    'payment-approval-security-alert',
    '{
      "apiVersion": "openslo/v1",
      "kind": "AlertPolicy",
      "metadata": {
        "name": "payment-approval-security-alert",
        "displayName": "Payment approval security ALERT"
      },
      "spec": {
        "description": "Email when a payment APPROVE attempt is denied with ALERT severity.",
        "alertWhenBreaching": true,
        "alertWhenResolved": true,
        "alertWhenNoData": false,
        "conditions": [
          { "conditionRef": "payment-approval-security-condition" }
        ],
        "notificationTargets": [
          { "targetRef": "observability-mesh-email" }
        ]
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;
