# Builds the nsai-deterministic-gate local-mode demo.
#   docker compose up --build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY nsai-gate-spring-boot-starter/build.gradle nsai-gate-spring-boot-starter/
COPY nsai-gate-demo/build.gradle nsai-gate-demo/
# Cache dependencies in their own layer
RUN sh ./gradlew --no-daemon :nsai-gate-demo:dependencies > /dev/null
COPY nsai-gate-spring-boot-starter/src nsai-gate-spring-boot-starter/src
COPY nsai-gate-demo/src nsai-gate-demo/src
RUN sh ./gradlew --no-daemon :nsai-gate-demo:bootJar \
 && find nsai-gate-demo/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 nsai
COPY --from=build /workspace/app.jar /app/app.jar
USER nsai
ENV SPRING_PROFILES_ACTIVE=local
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
