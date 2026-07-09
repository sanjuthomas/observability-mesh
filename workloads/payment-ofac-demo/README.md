# Payment / OFAC demo workload

Policy-aware SSI cash instruction and payment lifecycle demo (trimmed port of [policy-pilot](https://github.com/sanjuthomas/policy-pilot)). Emits `sanction_scan_completed_total` and other telemetry consumed by the observability mesh SLOs.

## Services

| Service | Port | Role |
|---------|------|------|
| `instruction-service` | 9000 | Cash instruction lifecycle |
| `payment-service` | 9093 | Payment lifecycle + OFAC scan request creation |
| `ofac-service` | 9096 | Sanction scan simulator |
| `authorization-service` | 9094 | OPA-backed policy evaluation |
| `sequence-service` | 9095 | ID sequences |
| `demo-harness` | 9091 | Demo actions and seeding |

Also includes **MongoDB**, **OPA**, **opa-policy-seed**, and **Keycloak** (`oidc/`) used only by this workload. Platform mesh services (SLO author/provisioner, Grafana, etc.) run without authentication for now.

## Commands

From this workload directory (canonical entry point):

```bash
docker compose up -d --build
./scripts/seed-demo-data.sh --seed-only
```

From the **repository root** (convenience shim):

```bash
docker compose up -d
./workloads/payment-ofac-demo/scripts/seed-demo-data.sh --seed-only
```

```bash
./mvnw -pl workloads/payment-ofac-demo/payment-service -am verify
./mvnw -pl workloads/payment-ofac-demo -am verify
```

## Layout

```
workloads/payment-ofac-demo/
├── docker-compose.yml      # includes platform/ + workload services
├── oidc/keycloak-seed/     # workload identity (Keycloak realm + users)
├── postgres/seed-slos.sql  # workload-specific OpenSLO seed
├── pom.xml                 # Maven aggregator
├── scripts/seed-demo-data.sh
├── shared/                 # payment-ofac-common, payment-ofac-auth, payment-ofac-telemetry, clients
├── authorization-service/
├── demo-harness/
├── instruction-service/
├── ofac-service/
├── opa-policy-seed/
├── payment-service/
└── sequence-service/
```

Platform libraries (`shared/observability-mesh-auth`, `common`, `telemetry`) live at the repo root for SLO services. This workload keeps its own copies under `shared/payment-ofac-*` so it does not depend on root shared modules.
