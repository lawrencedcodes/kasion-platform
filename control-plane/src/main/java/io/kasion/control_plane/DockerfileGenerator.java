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
            # 🏗️ STAGE 1: Build the Application (Standard JVM)
            # ---------------------------------------------------------
            FROM eclipse-temurin:21-jdk-jammy as builder
            WORKDIR /app
            
            # 1. Copy Maven Wrapper & Dependencies first (Better Caching)
            COPY .mvn/ .mvn
            COPY mvnw pom.xml ./
            RUN chmod +x mvnw
            
            # 2. Copy Source Code
            COPY src ./src
            
            # 3. Build the JAR (Standard Spring Boot Build)
            # We skip tests to make it faster for the demo
            RUN ./mvnw clean package -DskipTests
            
            # ---------------------------------------------------------
            # 🚀 STAGE 2: Create the Lightweight Runtime
            # ---------------------------------------------------------
            FROM eclipse-temurin:21-jre-jammy
            WORKDIR /app
            
            # Create a non-root user for security
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            
            # Copy the JAR from the builder stage
            # We use a wildcard *.jar so we don't need to know the artifactId
            COPY --from=builder /app/target/*.jar app.jar
            
            # Expose the standard port
            EXPOSE 8080
            
            # Start the app
            ENTRYPOINT ["java", "-jar", "app.jar"]
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