# ── Stage 1: build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency downloads separately from source changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

# ── Stage 2: run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# wiki, sources, and watcher-state directories are expected as volume mounts
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
