package io.kasion.control_plane;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerfileGeneratorTest {

    private final DockerfileGenerator generator = new DockerfileGenerator();

    @Test
    void testStandardBuild_WithWrapper() {
        String dockerfile = generator.generateStandardBuild("21", true);
        System.out.println("Generated Dockerfile (Wrapper):\n" + dockerfile);

        assertTrue(dockerfile.contains("FROM eclipse-temurin:21-jdk-jammy as builder"),
                "Should use JDK base image for wrapper build");
        assertTrue(dockerfile.contains("COPY . ."),
                "Should copy all files for wrapper build");
        assertTrue(dockerfile.contains("./mvnw clean package"),
                "Should execute mvnw");
        assertTrue(dockerfile.contains("chmod +x mvnw"),
                "Should make mvnw executable");
    }

    @Test
    void testStandardBuild_WithoutWrapper() {
        String dockerfile = generator.generateStandardBuild("21", false);
        System.out.println("Generated Dockerfile (No Wrapper):\n" + dockerfile);

        assertTrue(dockerfile.contains("FROM maven:3.9-eclipse-temurin-21 as builder"),
                "Should use Maven base image for fallback build");
        assertTrue(dockerfile.contains("RUN mvn clean package"),
                "Should execute system mvn");
    }
}