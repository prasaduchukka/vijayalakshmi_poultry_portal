# Multi-stage build: compile with Maven, run on a slim JRE.
# Render builds this automatically when you pick "Docker" as the runtime.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependencies separately so code-only changes don't re-download the internet.
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render provides $PORT at runtime; application.yml already reads it via ${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
