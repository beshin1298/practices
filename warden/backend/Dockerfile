# Backend image: build the jar with Maven, run it on a slim JRE as a non-root user.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Dependencies first: this layer is reused until pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests run in verify.sh (Testcontainers needs a Docker daemon, which a build step does not have).
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S warden && adduser -S warden -G warden
COPY --from=build /app/target/*.jar app.jar
USER warden
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
