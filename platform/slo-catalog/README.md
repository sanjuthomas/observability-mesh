# SLO catalog bundle

Single image that packs what this repo **builds** for the OpenSLO path:

| Process | Port | Role |
|---------|------|------|
| PostgreSQL (`open_slo`) | 5432 | Catalog store |
| `slo-author-service` | 9090 | Author / validate / version OpenSLO docs |
| `slo-provisioner-service` | 9097 | Sloth → Prometheus rules (+ metric alert policies) |

Upstream mesh pieces (collector, Prometheus, Grafana, …) stay separate images and compose with this bundle.

## Image

```text
ghcr.io/sanjuthomas/observability-mesh-slo-catalog:<tag>
```

## Build locally

From the **repository root**:

```bash
docker build -f platform/slo-catalog/Dockerfile -t ghcr.io/sanjuthomas/observability-mesh-slo-catalog:0.1.0 .
```

## Run (with Prometheus for rule reload)

```bash
docker compose -f platform/slo-catalog/docker-compose.example.yml up -d --build
```

Then open:

- Author UI: http://localhost:9090/ui/
- Provisioner UI: http://localhost:9097/ui/
- Prometheus: http://localhost:9092

## Volumes

| Mount | Purpose |
|-------|---------|
| `/var/lib/postgresql/data` | Catalog persistence |
| `/rules` | Shared with Prometheus (`rule_files` → `rules-sloth`) |
| `/docker-entrypoint-initdb.d/02-*.sql` | Optional OpenSLO seed (first init only) |

## Important env

| Variable | Default | Notes |
|----------|---------|-------|
| `OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES` | `prometheus` | Must match `metricSourceRef` in SLIs |
| `PROMETHEUS_RELOAD_URL` | `http://prometheus:9090/-/reload` | Reachable Prometheus on the compose network |
| `OBSERVABILITY_MESH_AUTH_ENABLED` | `false` | Enable and set OIDC env for Keycloak |

## Publish

Pushes to `main` (and version tags) build and publish the image via `.github/workflows/publish-slo-catalog.yml` to GHCR.
