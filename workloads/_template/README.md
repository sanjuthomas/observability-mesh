# Workload template

Copy this directory to start a new demo workload with an **isolated** observability mesh tenant (own Postgres, Prometheus, Grafana, SLO author/provisioner, etc.).

```bash
cp -R workloads/_template workloads/my-workload
cd workloads/my-workload
cp .env.example .env
# edit docker-compose.yml: set name, builds, app services
docker compose up -d --build
```

## Isolation model

Each workload compose file:

1. Sets `name: <workload>` — scopes containers, networks, and volumes (`<workload>_postgres-data`, …)
2. `include`s `../../platform/docker-compose.yml` — brings up the full platform stack inside that project
3. Ships a workload `.env` — assigns **unique host ports** so multiple workloads can run in parallel

Internal DNS names (`postgres`, `prometheus`, `keycloak`, …) stay the same inside each project network. Only **host-published ports** and **Docker project scope** differ between workloads.

Do **not** set `container_name` on services — it prevents parallel tenants on one machine.

## Port allocation (`PORT_BLOCK`)

Use non-overlapping `PORT_BLOCK` values per workload on a shared laptop:

| Workload | `PORT_BLOCK` | Postgres | Grafana | SLO author | Keycloak |
|----------|--------------|----------|---------|------------|----------|
| `payment-ofac-demo` | `0` | 5432 | 3000 | 9090 | 9080 |
| `_template` / second demo | `2000` | 7432 | 5000 | 11090 | 11080 |
| third demo (suggested) | `4000` | 9432 | 7000 | 13090 | 13080 |

**Formula:** `host_port = base_port + PORT_BLOCK` (see `payment-ofac-demo/.env` for base ports).

Copy `.env.example` → `.env` and adjust `PORT_BLOCK` before `docker compose up`.

## Required workload files

```
workloads/my-workload/
├── .env                      # unique PORT_BLOCK + port overrides
├── docker-compose.yml        # include platform + identity + apps
├── postgres/seed-slos.sql      # OpenSLO seed for this workload
├── oidc/keycloak-seed/         # Keycloak realm + users (copy from payment-ofac-demo)
└── pom.xml / services…         # your application code
```

## `docker-compose.yml` checklist

- [ ] `name: my-workload` matches directory and `.env` project
- [ ] `include: ../../platform/docker-compose.yml`
- [ ] `postgres` volume override adds `./postgres/seed-slos.sql`
- [ ] `slo-provisioner-service` sets `OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES` to match seed `metricSourceRef`
- [ ] Optional: enable SLO auth via Keycloak overrides (see `payment-ofac-demo`)
- [ ] Keycloak `KC_HOSTNAME_PORT` and `OIDC_ISSUER_URL` use `${KEYCLOAK_PORT}` (browser OIDC)
- [ ] App services use **container** URLs for inter-service calls (`http://payment-service:9093`, not localhost)

## Running two workloads in parallel

```bash
# Terminal 1
cd workloads/payment-ofac-demo && docker compose up -d

# Terminal 2
cd workloads/claims-demo && docker compose up -d   # PORT_BLOCK=2000 in .env
```

Each stack is fully isolated: separate SLO catalog, metrics TSDB, traces, logs index, and workload data.

## Tear down one tenant

```bash
cd workloads/my-workload
docker compose down -v   # -v removes tenant volumes (Postgres, Prometheus, …)
```
