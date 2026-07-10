# Observability Mesh SLO catalog bundle:
# PostgreSQL (open_slo) + slo-author-service + slo-provisioner-service (+ Sloth).
#
# Build from repo root:
#   docker build -f platform/slo-catalog/Dockerfile -t sanjuthomas/observability-mesh-slo-catalog:0.1.0 .

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY . .
RUN chmod +x mvnw \
    && ./mvnw -pl slo-author-service,slo-provisioner-service -am package -DskipTests -q

FROM postgres:16-alpine

ARG SLOTH_VERSION=v0.16.0
ARG TARGETARCH=amd64

RUN apk add --no-cache openjdk21-jre-headless curl \
    && ARCH="${TARGETARCH}" \
    && if [ "${ARCH}" = "amd64" ]; then SLOTH_ARCH=amd64; \
       elif [ "${ARCH}" = "arm64" ]; then SLOTH_ARCH=arm64; \
       else SLOTH_ARCH=amd64; fi \
    && curl -fsSL "https://github.com/slok/sloth/releases/download/${SLOTH_VERSION}/sloth-linux-${SLOTH_ARCH}" \
        -o /usr/local/bin/sloth \
    && chmod +x /usr/local/bin/sloth

WORKDIR /app
COPY --from=build /src/slo-author-service/target/slo-author-service-*.jar /app/slo-author-service.jar
COPY --from=build /src/slo-provisioner-service/target/slo-provisioner-service-*.jar /app/slo-provisioner-service.jar
COPY postgres/init.sql /docker-entrypoint-initdb.d/01-init.sql
COPY platform/slo-catalog/entrypoint.sh /usr/local/bin/slo-catalog-entrypoint.sh
RUN chmod +x /usr/local/bin/slo-catalog-entrypoint.sh \
    && mkdir -p /rules /work

ENV POSTGRES_USER=open_slo \
    POSTGRES_PASSWORD=open_slo \
    POSTGRES_DB=open_slo \
    SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/open_slo \
    SPRING_DATASOURCE_USERNAME=open_slo \
    SPRING_DATASOURCE_PASSWORD=open_slo \
    OBSERVABILITY_MESH_AUTH_ENABLED=false \
    PROMETHEUS_RULES_DIR=/rules \
    SLO_PROVISIONER_WORK_DIR=/work \
    SLOTH_BINARY=/usr/local/bin/sloth \
    PROMETHEUS_RELOAD_URL=http://prometheus:9090/-/reload \
    OBSERVABILITY_MESH_SLO_PROVISIONER_DATASOURCE_NAMES=prometheus

EXPOSE 5432 9090 9097
VOLUME ["/var/lib/postgresql/data", "/rules"]

ENTRYPOINT ["/usr/local/bin/slo-catalog-entrypoint.sh"]
