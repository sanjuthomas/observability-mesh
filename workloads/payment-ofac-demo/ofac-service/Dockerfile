FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -pl workloads/payment-ofac-demo/ofac-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/workloads/payment-ofac-demo/ofac-service/target/ofac-service-*.jar app.jar
EXPOSE 9096
ENTRYPOINT ["java", "-jar", "app.jar"]
