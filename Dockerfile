## Runtime-only image: copy prebuilt JAR into container and run
FROM mcr.microsoft.com/openjdk/jdk:17-ubuntu
WORKDIR /app

# Copy the prebuilt jar from host (run mvn package before docker build)
COPY target/compensation-0.0.1-SNAPSHOT.jar /app/app.jar

# Default env (override in compose if needed)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
