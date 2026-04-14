# syntax=docker/dockerfile:1

FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

COPY build.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Cache dependencies before copying source
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
