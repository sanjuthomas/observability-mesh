# Platform compose bundle

Reusable observability mesh services included by each workload via:

```yaml
include:
  - path: ../../platform/docker-compose.yml
```

Each workload compose project gets its **own** collector, Prometheus, Tempo, Grafana, OpenSearch, PostgreSQL, SLO author, and SLO provisioner — scoped by Docker Compose project name (set `name: <workload>` in the workload file).

## Services

| Service | Default host port | Role |
|---------|-------------------|------|
| `otel-collector` | 4317 (gRPC), 4318 (HTTP), 8889 (metrics) | OTLP ingest; fans out logs, metrics, traces |
| `prometheus` | 9092 | Metrics TSDB; scrapes collector `:8889`; evaluates alert rules |
| `alertmanager` | 9098 | Routes firing **metric-based** Prometheus alerts to email (SMTP via workload `.env`) |
| `tempo` | 3200 | Trace storage |
| `grafana` | 3000 | Dashboards (Prometheus + Tempo datasources, SLO Overview) |
| `opensearch` | 9200 | Log storage (`otel-logs*` index) |
| `opensearch-dashboards` | 5601 | Log exploration |
| `postgres` | 5432 | SLO catalog (`open_slo` database) |
| `slo-author-service` | 9090 | OpenSLO authoring UI + API |
| `slo-provisioner-service` | 9097 | Sloth batch + provisioner browser UI |

## Packaged image (author + provisioner + Postgres)

For demos and external workloads, the three catalog processes are also published as one image:

- Image: `sanjuthomas/observability-mesh-slo-catalog`
- Docs: [platform/slo-catalog/README.md](slo-catalog/README.md)
- Ships **schema only** — OpenSLO documents (SLO / SLI / Alert*) come from the consuming workload

Upstream collector / Prometheus / Grafana remain separate compose services.

Host ports are set in the **workload** `.env` file (see [workloads/_template/.env.example](../workloads/_template/.env.example)). Formula: `host_port = base_port + PORT_BLOCK`.

Do **not** set `container_name` on services — compose project name scopes containers and volumes per workload.

## Configuration files

Mounted from the repository root (relative to `platform/docker-compose.yml`):

| File | Service |
|------|---------|
| [otel-collector-config.yaml](../otel-collector-config.yaml) | `otel-collector` |
| [prometheus/prometheus.yml](../prometheus/prometheus.yml) | `prometheus` |
| [prometheus/alertmanager.yml.template](../prometheus/alertmanager.yml.template) | `alertmanager` (rendered at startup) |
| [tempo/tempo.yaml](../tempo/tempo.yaml) | `tempo` |
| [grafana/](../grafana/) | `grafana` (provisioning) |
| [postgres/init.sql](../postgres/init.sql) | `postgres` (schema) |

Workloads add tenant-specific seeds by overriding the `postgres` volume mount (e.g. `./postgres/seed-slos.sql`).

## Workload overrides

Typical overrides in `workloads/<name>/docker-compose.yml`:

- **Ports** — via `.env` (`PORT_BLOCK`, per-service `*_PORT` variables)
- **SLO seeds** — `postgres/seed-slos.sql` mounted into initdb
- **Provisioner datasource** — `OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES`
- **Auth** — platform defaults to `OBSERVABILITY_MESH_AUTH_ENABLED=false`; workloads can re-enable Keycloak JWT (see [payment-ofac-demo](../workloads/payment-ofac-demo/docker-compose.yml))
- **Identity** — Keycloak and OIDC config live in the workload, not here

## Parallel tenants

See [workloads/_template/README.md](../workloads/_template/README.md) for copying a workload, assigning a non-overlapping `PORT_BLOCK`, and running two stacks on one machine.
