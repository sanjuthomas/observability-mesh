-- Reference OpenSLO documents for sanction-scan SLOs (seeded on first Postgres init).
-- Metrics (sanction_scan_completed_total) are not emitted yet; instrumentation follows.

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
