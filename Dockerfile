# Multi-stage build: compile with a full JDK, run on a slim JRE as a non-root user.

# ---------- build stage ----------
FROM docker.io/library/eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 1) Wrapper + pom first: this layer (and the dependency download) is cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# 2) Sources: only this and later layers rebuild on a code change.
COPY src src
# Tests run in CI (./mvnw verify), not in the image build.
RUN ./mvnw -B -q package -DskipTests

# 3) Split the fat jar into layers ordered by change frequency (Boot 4 "tools" jar mode).
RUN java -Djarmode=tools -jar target/market-lens-*.jar extract --layers --launcher --destination extracted

# ---------- runtime stage ----------
FROM docker.io/library/eclipse-temurin:21-jre

# Non-root user. (curl, used by the compose healthcheck, already ships with this base image.)
RUN useradd --uid 1001 --no-create-home --shell /usr/sbin/nologin spring

WORKDIR /app
# Least-changing layers first, our own code last.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
