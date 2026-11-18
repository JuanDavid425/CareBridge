# Build: docker compose build api   (context: apps/api)
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 carebridge
WORKDIR /app
COPY --from=build /src/target/carebridge-api-*.jar app.jar
USER carebridge
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
