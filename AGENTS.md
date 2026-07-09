# AGENTS.md

Guidance for AI coding agents working in **Observability Mesh** (`observability-mesh`).

## Project summary

**Observability Mesh** — Java monorepo with a composable observability stack (**PostgreSQL SLO catalog**, **otel-collector**, **Prometheus**, **Tempo**, **Grafana**, **OpenSearch**, **Keycloak**, **SLO author**, **SLO provisioner**) and a demo workload under `workloads/payment-ofac-demo/` (policy-aware SSI cash instruction/payment lifecycle + OFAC sanction scans on **MongoDB**).

Stack: Java **21**, Maven Wrapper (`./mvnw`), JaCoCo (**80% minimum overall coverage** per module).

---

## Test coverage policy (required)

**Minimum overall coverage: 80%** on each service module bundle, enforced by JaCoCo during `./mvnw verify`.

Agents **must**:

1. Run `./mvnw verify` after code changes — not `./mvnw test` alone.
2. Add or update tests when new behavior would drop coverage below 80%.
3. Not lower `jacoco.minimum.coverage` without explicit maintainer approval.

---

## Spring Boot version policy (required)

**Authoritative version:** `spring-boot-starter-parent` in root `pom.xml` (currently **4.1.0**).

Agents **must**:

1. Keep the project on **Spring Boot 4.x**.
2. Use **Boot 4 modular starters** (`spring-boot-starter-webmvc`, not `spring-boot-starter-web`).
3. Align **springdoc** with Boot 4: **springdoc 3.x** (`${springdoc.version}`).
4. Use `@MockitoBean` / `@MockitoSpyBean` — not `@MockBean`.

---

## Code conventions

- Package root: `com.observabilitymesh`
- DTOs: Java `record`s with Jakarta validation
- Persistence: demo services — Spring Data MongoDB + bitemporal `in`/`out` versioning; SLO catalog — Spring Data JPA / JDBC on PostgreSQL (`open_slo`, JSONB `content`)
- Errors: `ResponseStatusException` + `@ControllerAdvice` → JSON `ApiError`
- Auth: Keycloak JWT + OBO headers (`X-On-Behalf-Of`)

---

## Commands

```bash
docker compose up -d                    # repo-root shim → platform + payment-ofac-demo workload
# Or: docker compose -f workloads/payment-ofac-demo/docker-compose.yml up -d
./mvnw verify                           # tests + JaCoCo gate
./workloads/payment-ofac-demo/scripts/seed-demo-data.sh             # demo data via harness (workload)
./mvnw -pl workloads/payment-ofac-demo/payment-service -am verify
```

| URL | Service |
|-----|---------|
| http://localhost:9000/ui/ | Instruction browser |
| http://localhost:9093/ui/ | Payment browser |
| http://localhost:9090/ui/ | SLO author service (OpenSLO authoring) |
| http://localhost:9096/ui/ | OFAC scan browser |
| http://localhost:9097/ui/ | SLO provisioner browser |
| http://localhost:9094/ui/ | Authorization user directory |
| http://localhost:9091 | Demo harness |
| http://localhost:9080 | Keycloak admin |
| http://localhost:3000 | Grafana (metrics & traces) |
| http://localhost:5601 | OpenSearch Dashboards (logs) |

---

## Do not

- Commit secrets, `.env`, or credentials.
- Add Kafka, Neo4j, or chat/indexer components.
- Introduce Boot 3 starters or springdoc 2.x.
