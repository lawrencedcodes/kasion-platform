package io.kasion.control_plane;

import org.springframework.stereotype.Service;

@Service
public class DockerfileGenerator {

    /**
     * 🆕 OPTION A: Standard JVM Build (Reliable, Fast, Compatible)
     * This is our new Default.
     * @param useWrapper If true, uses the project's 'mvnw'. If false, uses system 'mvn'.
     */
    public String generateStandardBuild(String javaVersion, boolean useWrapper) {
        String buildStage;

        if (useWrapper) {
            // ---------------------------------------------------------
            // 🏗️ STRATEGY 1: Wrapper Build (Preferred)
            // ---------------------------------------------------------
            // Uses the project's exact Maven version via 'mvnw'
            buildStage = """
                FROM eclipse-temurin:21-jdk-jammy as builder
                WORKDIR /app
                
                # 1. Copy Project Files
                COPY . .
                
                # 2. Build using the wrapper
                # Ensure execution permissions and skip tests
                RUN chmod +x mvnw && ./mvnw clean package -DskipTests
                """;
        } else {
            // ---------------------------------------------------------
            // 🏗️ STRATEGY 2: Fallback Build (Universal)
            // ---------------------------------------------------------
            // Uses a pre-installed Maven 3.9 environment
            buildStage = """
                FROM maven:3.9-eclipse-temurin-21 as builder
                WORKDIR /app
                
                # 1. Copy Project Files
                COPY . .
                
                # 2. Build using system Maven
                RUN mvn clean package -DskipTests
                """;
        }

        return buildStage + """
            
            # ---------------------------------------------------------
            # 🚀 STAGE 2: Create the Runtime
            # ---------------------------------------------------------
            FROM eclipse-temurin:21-jre-jammy
            WORKDIR /app
            
            # Security: Create a non-root user
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            
            # Copy the JMX Exporter agent and config
            COPY control-plane/jmx_exporter/jmx_prometheus_javaagent.jar /app/jmx_prometheus_javaagent.jar
            COPY control-plane/jmx_exporter/jmx_config.yml /app/jmx_config.yml

            # Copy the JAR from the builder stage
            COPY --from=builder /app/target/*.jar app.jar
            
            # Expose application port and JMX Exporter port
            EXPOSE 8080
            EXPOSE 9404
            ENTRYPOINT ["java", "-javaagent:/app/jmx_prometheus_javaagent.jar=9404:/app/jmx_config.yml", "-Dserver.port=8080", "-jar", "app.jar"]
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