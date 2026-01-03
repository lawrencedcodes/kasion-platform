package io.kasion.control_plane;

import org.springframework.stereotype.Service;

@Service
public class DockerfileGenerator {

    /**
     * This is the "Secret Sauce".
     * Instead of asking the user for a Dockerfile, we generate the perfect one
     * optimized for GraalVM Native Images.
     */
    public String generateNativeBuild(String artifactId, String javaVersion) {
        return """
            # ---------------------------------------------------------------------------
            # STAGE 1: The Furnace (Build Stage)
            # We use the official GraalVM Community image to compile the Java code.
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
            # -DskipTests speeds up the build (we run tests in a separate CI step ideally)
            RUN ./mvnw -Pnative native:compile -DskipTests
            
            # ---------------------------------------------------------------------------
            # STAGE 2: The Runtime (Production Stage)
            # We use a tiny 'Distroless' or minimal Linux image. No JVM required!
            # ---------------------------------------------------------------------------
            FROM ubuntu:jammy-minimal
            
            # Create a non-root user for security (Senior Devs love this)
            RUN groupadd -r kasion && useradd -r -g kasion kasion
            USER kasion
            
            WORKDIR /app
            
            # Copy ONLY the binary from the builder stage
            # Notice we use the detected artifactId here
            COPY --from=builder /app/target/%s /app/runner
            
            # The startup command is just the binary name
            ENTRYPOINT ["/app/runner"]
            """.formatted(artifactId);
    }
}
