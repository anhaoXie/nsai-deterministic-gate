# Builds the nsai-deterministic-gate local-mode demo.
#   docker compose up --build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Cache dependencies in their own layer
RUN sh ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN sh ./gradlew --no-daemon bootJar \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 nsai
COPY --from=build /workspace/app.jar /app/app.jar
USER nsai
ENV SPRING_PROFILES_ACTIVE=local
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
