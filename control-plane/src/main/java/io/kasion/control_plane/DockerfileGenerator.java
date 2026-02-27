package io.kasion.control_plane;

import org.springframework.stereotype.Service;

@Service
public class DockerfileGenerator {

    /**
     * 🆕 OPTION A: Standard JVM Build (Reliable, Fast, Compatible)
     * This is our new Default.
     * @param useWrapper If true, uses the project's 'mvnw'. If false, uses system 'mvn'.
     */
    public String generateStandardBuild(String javaVersion, BuildEngine.BuildTool buildTool) {
        String buildStage;

        if (buildTool == BuildEngine.BuildTool.MAVEN) {
            // ---------------------------------------------------------
            // 🏗️ STRATEGY 1: Maven Wrapper Build
            // ---------------------------------------------------------
            buildStage = """
                FROM eclipse-temurin:21-jdk-jammy as builder
                WORKDIR /app
                
                # 1. Copy Project Files
                COPY . .
                
                # 2. Build using the wrapper
                # Ensure execution permissions and skip tests
                RUN chmod +x mvnw && ./mvnw clean package -DskipTests
                """;
        } else if (buildTool == BuildEngine.BuildTool.GRADLE) {
            // ---------------------------------------------------------
            // 🏗️ STRATEGY 2: Gradle Wrapper Build
            // ---------------------------------------------------------
            buildStage = """
                FROM gradle:8-jdk21 as builder
                WORKDIR /app
                
                # 1. Copy Project Files
                COPY . .
                
                # 2. Build using the wrapper
                # Ensure execution permissions and skip tests
                RUN chmod +x gradlew && ./gradlew bootJar --no-daemon
                """;
        } else {
            throw new IllegalArgumentException("Unsupported build tool: " + buildTool);
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

            # Copy the Jolokia agent
            COPY control-plane/jolokia/jolokia-jvm-agent.jar /app/jolokia-jvm-agent.jar

            # Copy the JAR from the builder stage
            COPY --from=builder /app/build/libs/*.jar app.jar
            
            # Expose application port and JMX Exporter port and Jolokia port
            EXPOSE 8080
            EXPOSE 9404
            EXPOSE 8778
            ENTRYPOINT ["java", "-javaagent:/app/jmx_prometheus_javaagent.jar=9404:/app/jmx_config.yml", "-javaagent:/app/jolokia-jvm-agent.jar=port=8778,host=0.0.0.0", "-Dserver.port=8080", "-jar", "app.jar"]
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