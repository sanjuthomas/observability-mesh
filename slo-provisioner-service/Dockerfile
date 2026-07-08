FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -pl slo-provisioner-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
ARG SLOTH_VERSION=v0.16.0
RUN apk add --no-cache curl \
    && curl -fsSL "https://github.com/slok/sloth/releases/download/${SLOTH_VERSION}/sloth-linux-amd64" \
        -o /usr/local/bin/sloth \
    && chmod +x /usr/local/bin/sloth
WORKDIR /app
COPY --from=build /src/slo-provisioner-service/target/slo-provisioner-service-*.jar app.jar
RUN mkdir -p /rules /work
ENV PROMETHEUS_RULES_DIR=/rules
ENV SLO_PROVISIONER_WORK_DIR=/work
ENV SLOTH_BINARY=/usr/local/bin/sloth
EXPOSE 9097
ENTRYPOINT ["java", "-jar", "app.jar"]
