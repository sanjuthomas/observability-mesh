FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -pl workloads/payment-ofac-demo/payment-service -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/workloads/payment-ofac-demo/payment-service/target/payment-service-*.jar app.jar
EXPOSE 9093
ENTRYPOINT ["java", "-jar", "app.jar"]
