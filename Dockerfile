# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the Maven wrapper ----------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies in a separate layer so source changes do not invalidate the dependency cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/bookingcore-api-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Runtime stage: JRE only, non-root, layered for cache-friendly rebuilds -------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system bookingcore && useradd --system --gid bookingcore --no-create-home bookingcore
WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER bookingcore
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
