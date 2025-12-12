# Stage 1: build the application
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

# Stage 2: build runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/target/ingest-api-0.0.1-SNAPSHOT-exec.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
