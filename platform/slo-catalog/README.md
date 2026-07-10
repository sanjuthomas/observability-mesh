# SLO catalog bundle

Single image that packs what this repo **builds** for the OpenSLO path:

| Process | Port | Role |
|---------|------|------|
| PostgreSQL (`open_slo`) | 5432 | Catalog store (schema only) |
| `slo-author-service` | 9090 | Author / validate / version OpenSLO docs |
| `slo-provisioner-service` | 9097 | Sloth → Prometheus rules (+ metric alert policies) |

**No OpenSLO documents are baked into the image** — no SLOs, SLIs, or Alert policies. The database starts with an empty catalog schema (`postgres/init.sql`). Workloads (e.g. a future Petstore demo) supply their own seeds or author documents via the UI/API.

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

To seed documents on first Postgres init, the **consuming** compose (not this image) can mount SQL into `/docker-entrypoint-initdb.d/` (e.g. `02-seed-slos.sql` from a Petstore repo).

## Important env

| Variable | Default | Notes |
|----------|---------|-------|
| `OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES` | `prometheus` | Must match `metricSourceRef` in SLIs |
| `PROMETHEUS_RELOAD_URL` | `http://prometheus:9090/-/reload` | Reachable Prometheus on the compose network |
| `OBSERVABILITY_MESH_AUTH_ENABLED` | `false` | Enable and set OIDC env for Keycloak |

## Publish

Pushes to `main` (and version tags) build and publish the image via `.github/workflows/publish-slo-catalog.yml` to GHCR.
