# syntax=docker/dockerfile:1

# --- build stage: compile + package the Spring Boot fat jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copy the POM first and pre-fetch dependencies so this layer is cached across source changes.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# --- runtime stage: small JRE image (Java 21, as per the spec, regardless of host JDK) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/pulsegate-0.1.0.jar app.jar
EXPOSE 8080
# Honour container memory limits without hand-tuning -Xmx.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
