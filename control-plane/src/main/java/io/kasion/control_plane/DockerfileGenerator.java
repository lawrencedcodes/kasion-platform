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
            
            # 1. Copy Maven Wrapper & Dependencies
            COPY .mvn/ .mvn
            COPY mvnw pom.xml ./
            RUN chmod +x mvnw
            
            # 2. Copy Source Code
            COPY src ./src
            
            # 3. Build the JAR
            RUN ./mvnw clean package -DskipTests
            
            # ---------------------------------------------------------
            # 🚀 STAGE 2: Create the Runtime
            # ---------------------------------------------------------
            FROM eclipse-temurin:21-jre-jammy
            WORKDIR /app
            
            # Create a non-root user
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            
            # Copy the JAR
            COPY --from=builder /app/target/*.jar app.jar
            
            # Expose the standard port
            EXPOSE 8080
            
            # Start the app with forced port 8080
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