# syntax=docker/dockerfile:1

# ---- Build stage: compile the app with the full JDK and Maven ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Download dependencies first, in their own layer, so code changes don't re-download them.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests, formatting and coverage are checked in CI before any deploy, so they are skipped
# here to keep image builds fast.
RUN ./mvnw -B -q package -DskipTests -Dspring-javaformat.skip=true -Djacoco.skip=true \
    && cp target/salon-booking-api-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extracted

# ---- Run stage: only the Java runtime and the app, no build tools ----
FROM eclipse-temurin:25-jre

# Run as an unprivileged user, never as root.
RUN groupadd --system app && useradd --system --gid app --no-create-home app \
    && mkdir /app && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/ ./
USER app

# Tuned for small containers (e.g. Render's free plan: 512 MB RAM, 0.1 CPU):
#   Xmx320m               fixed heap size; leaves room for the JVM's other memory in 512 MB.
#                         Fixed, not a percentage: the AOT cache only loads when the heap
#                         matches the size used in the training run.
#   UseSerialGC           the lightest garbage collector, ideal for one small CPU
#   TieredStopAtLevel=1   faster startup with a slow CPU, at some cost to peak speed
#   Xss512k               smaller thread stacks
#   AOTCache              start from the cache created by the training run below
ENV JAVA_TOOL_OPTIONS="-Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

# Training run for Java's AOT cache (JEP 483/514): starts the app once during the build,
# records the classes it loads and links, and stops as soon as Spring has wired everything
# together (spring.context.exit=onRefresh). Real startups reuse the cache instead of redoing
# that work. No database is needed: migrations, schema validation and database metadata
# lookups are switched off for this run only.
#   -XX:-AOTClassLinking  leaves out pre-linked classes. They need the JVM's memory layout
#                         to match the training run exactly, which isn't true inside a
#                         memory-limited container; Java then refuses to start. Without
#                         them a mismatch just means a normal, slower start.
#   -Xlog:aot=error       hides harmless warnings about classes that can't be cached.
RUN java -XX:AOTCacheOutput=app.aot -XX:-AOTClassLinking -Xlog:aot=error -Dspring.context.exit=onRefresh -jar app.jar \
        --spring.profiles.active=prod \
        --spring.flyway.enabled=false \
        --spring.jpa.hibernate.ddl-auto=none \
        --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
        --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        --spring.datasource.url=jdbc:postgresql://localhost:5432/training-only \
        --app.security.jwt-secret=training-only-not-a-real-secret-0123456789 \
    && test -s app.aot
ENV JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -XX:AOTCache=app.aot"

# 8080: the API (public). 9090: metrics and health (internal only, never publish it).
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
