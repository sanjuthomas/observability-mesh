-- Workload-specific OpenSLO seed (loaded on first Postgres init only).
-- Replace datasource name and PromQL with metrics your workload emits.

INSERT INTO service_level_objectives (
    logical_key, version, stale, api_version, kind, name, content, created_by, created_at
) VALUES (
    'openslo/v1/SLI/example-availability',
    1,
    false,
    'openslo/v1',
    'SLI',
    'example-availability',
    '{
      "apiVersion": "openslo/v1",
      "kind": "SLI",
      "metadata": {
        "name": "example-availability",
        "displayName": "Example availability"
      },
      "spec": {
        "description": "Replace with a ratio metric for your workload.",
        "ratioMetric": {
          "good": {
            "metricSource": {
              "metricSourceRef": "my-workload-prometheus",
              "spec": {
                "query": "sum(rate(http_server_requests_seconds_count{service_name=\"my-service\",status=~\"2..\"}[5m]))"
              }
            }
          },
          "total": {
            "metricSource": {
              "metricSourceRef": "my-workload-prometheus",
              "spec": {
                "query": "sum(rate(http_server_requests_seconds_count{service_name=\"my-service\"}[5m]))"
              }
            }
          }
        }
      }
    }'::jsonb,
    'seed',
    TIMESTAMPTZ '2026-01-01T00:00:00Z'
) ON CONFLICT (logical_key, version) DO NOTHING;
