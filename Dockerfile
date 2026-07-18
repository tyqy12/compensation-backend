# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${MAVEN_IMAGE} AS backend-builder
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN chmod +x mvnw

COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM ${JRE_IMAGE} AS app-runtime
WORKDIR /app

# Keep the JVM process non-root while retaining compatibility with bind-mounted
# runtime directories created by Docker as root.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl gosu \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 compensation \
    && useradd --system --uid 10001 --gid compensation --home-dir /nonexistent --shell /usr/sbin/nologin compensation \
    && mkdir -p /app /var/log/compensation /var/lib/compensation/files \
    && chown -R compensation:compensation /app /var/log/compensation /var/lib/compensation/files

COPY --from=backend-builder --chown=compensation:compensation \
    /build/target/compensation-0.0.1-SNAPSHOT.jar /app/app.jar
COPY docker/app-entrypoint.sh /usr/local/bin/app-entrypoint.sh
RUN chmod 755 /usr/local/bin/app-entrypoint.sh

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/app-entrypoint.sh"]
