package io.kasion.control_plane;

import org.springframework.stereotype.Service;

@Service
public class DockerfileGenerator {

    /**
     * 🆕 OPTION A: Standard JVM Build (Reliable, Fast, Compatible)
     * This is our new Default.
     */
    public String generateStandardBuild(String javaVersion) {
        return """
            # ---------------------------------------------------------
            # 🏗️ STAGE 1: Build (Universal Maven Strategy)
            # ---------------------------------------------------------
            # We use a base image that HAS Maven installed (maven:3.9)
            # This fixes the "missing mvnw" error on older projects
            FROM maven:3.9-eclipse-temurin-21 as builder
            WORKDIR /app
            
            # 1. Copy Project Files
            COPY . .
            
            # 2. Build the JAR using the system 'mvn'
            # We skip tests to speed up the build
            RUN mvn clean package -DskipTests
            
            # ---------------------------------------------------------
            # 🚀 STAGE 2: Create the Runtime
            # ---------------------------------------------------------
            FROM eclipse-temurin:21-jre-jammy
            WORKDIR /app
            
            # Security: Create a non-root user
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            
            # Copy the JAR from the builder stage
            # We use a wildcard *.jar because we don't know the exact name
            COPY --from=builder /app/target/*.jar app.jar
            
            # Force port 8080
            EXPOSE 8080
            ENTRYPOINT ["java", "-Dserver.port=8080", "-jar", "app.jar"]
            """;
    }

    /**
     * 💎 OPTION B: Native Image Build (The "Pro" Feature)
     * Saved for later use.
     */
    public String generateNativeBuild(String artifactId, String javaVersion) {
        return """
            FROM ghcr.io/graalvm/native-image-community:21 AS builder
            WORKDIR /app
            COPY mvnw .
            COPY .mvn .mvn
            COPY pom.xml .
            COPY src src
            RUN ./mvnw -Pnative native:compile -DskipTests
            
            FROM ubuntu:jammy
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            WORKDIR /app
            COPY --from=builder /app/target/%s /app/runner
            ENTRYPOINT ["/app/runner"]
            """.formatted(artifactId);
    }
}