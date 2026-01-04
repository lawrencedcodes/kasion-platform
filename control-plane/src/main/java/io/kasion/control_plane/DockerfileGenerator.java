package io.kasion.control_plane;

import org.springframework.stereotype.Service;

@Service
public class DockerfileGenerator {

    /**
     * Generates a Multi-Stage Dockerfile optimized for Spring Boot Native Images.
     */
    public String generateNativeBuild(String artifactId, String javaVersion) {
        return """
            # ---------------------------------------------------------------------------
            # STAGE 1: The Furnace (Build Stage)
            # ---------------------------------------------------------------------------
            FROM ghcr.io/graalvm/native-image-community:21 AS builder
            
            WORKDIR /app
            
            # Optimization: Copy generic maven wrapper first to cache dependencies
            COPY mvnw .
            COPY .mvn .mvn
            COPY pom.xml .
            
            # Copy source code
            COPY src src
            
            # 🔧 THE MAGIC COMMAND
            # -Pnative triggers the Spring Boot Native profile
            # -DskipTests speeds up the build
            RUN ./mvnw -Pnative native:compile -DskipTests
            
            # ---------------------------------------------------------------------------
            # STAGE 2: The Runtime (Production Stage)
            # ---------------------------------------------------------------------------
            FROM ubuntu:jammy
            
            # Create a non-root user for security
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            
            WORKDIR /app
            
            # Copy ONLY the binary from the builder stage
            COPY --from=builder /app/target/%s /app/runner
            
            # The startup command
            ENTRYPOINT ["/app/runner"]
            """.formatted(artifactId);
    }
}
