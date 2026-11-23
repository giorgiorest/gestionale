# syntax=docker/dockerfile:1

# ---- Build ----
# Il jar è indipendente dall'architettura: si compila una volta sola sulla piattaforma della build
# (niente emulazione); solo l'immagine finale è per amd64 o arm64 (es. Oracle Cloud Ampere).
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# Prima solo wrapper e pom, per sfruttare la cache delle dipendenze.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Fine riga Unix anche se il repository è stato scaricato su Windows con CRLF.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw \
    && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests && cp target/gestionale-api-*.jar app.jar

# ---- Runtime ----
FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
