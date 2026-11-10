# syntax=docker/dockerfile:1.7
# ------------------------------------------------------------------------------------------------
# One Dockerfile for every service:  docker build --build-arg SERVICE=order-service .
# Stage 1 builds the whole Maven reactor once (identical for all services -> cached by BuildKit).
# Stage 2 explodes the Spring Boot jar into layers (dependencies change rarely, app code often).
# Stage 3 is a small JRE image running as a non-root user.
# ------------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY libs libs
COPY services services
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:25-jre AS layers
ARG SERVICE
WORKDIR /layers
COPY --from=build /workspace/services/${SERVICE}/target/${SERVICE}.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre
ARG SERVICE
LABEL org.opencontainers.image.title="${SERVICE}"
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER app
# Container-aware heap sizing; exit on OOM so the orchestrator restarts a healthy instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
