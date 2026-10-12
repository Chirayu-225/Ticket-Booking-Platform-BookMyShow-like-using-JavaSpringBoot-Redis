# Multi-stage build: stage 1 has Maven + Node (Vaadin's frontend build needs both);
# stage 2 ships only the final JAR on a slim JRE. Same pattern as Issue Tracker's
# Dockerfile, extended with the npm/pnpm step Vaadin's production build needs.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# -Pproduction activates vaadin.productionMode=true (see pom.xml profile),
# which minifies and bundles the frontend instead of shipping a dev build.
RUN mvn -q -B package -Pproduction -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app/target/ticket-booking-1.0.0.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]
