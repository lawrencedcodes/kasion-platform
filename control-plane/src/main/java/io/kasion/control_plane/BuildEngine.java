package io.kasion.control_plane;

import org.eclipse.jgit.api.Git;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class BuildEngine {

    private final DeploymentRepository deploymentRepository;
    private final DockerfileGenerator dockerfileGenerator;

    // Inject the new Generator
    public BuildEngine(DeploymentRepository deploymentRepository, DockerfileGenerator dockerfileGenerator) {
        this.deploymentRepository = deploymentRepository;
        this.dockerfileGenerator = dockerfileGenerator;
    }

    @Async
    public void startBuild(String deploymentId) {
        String jobId = deploymentId.substring(0, 8);
        System.out.println("🚀 [Job " + jobId + "] Engine started.");

        try {
            // 1. Create Workspace
            Path workingDir = Files.createTempDirectory("kasion-build-" + jobId);
            System.out.println("📂 [Job " + jobId + "] Workspace created: " + workingDir);

            updateStatus(deploymentId, "CLONING");

            // 2. Clone (Hardcoded for prototype)
            String demoRepoUrl = "https://github.com/spring-petclinic/spring-petclinic-rest.git";
            try (Git git = Git.cloneRepository()
                    .setURI(demoRepoUrl)
                    .setDirectory(workingDir.toFile())
                    .call()) {
                System.out.println("✅ [Job " + jobId + "] Code cloned.");
            }

            // 3. Analyze & Generate Plan
            updateStatus(deploymentId, "ANALYZING");
            File pomFile = new File(workingDir.toFile(), "pom.xml");

            if (pomFile.exists()) {
                // Use the fixed parser
                String artifactId = parseArtifactId(pomFile);
                System.out.println("📦 [Job " + jobId + "] Identified App: " + artifactId);

                System.out.println("🧠 [Job " + jobId + "] Generating Native Image Strategy...");
                String dockerfileContent = dockerfileGenerator.generateNativeBuild(artifactId, "21");

                // Write Dockerfile to disk
                Files.writeString(workingDir.resolve("Dockerfile"), dockerfileContent);
                System.out.println("📝 [Job " + jobId + "] Dockerfile written to disk.");

                // 4. EXECUTION (The new part)
                updateStatus(deploymentId, "BUILDING_IMAGE");
                System.out.println("🐳 [Job " + jobId + "] Sending build context to Docker Daemon...");
                System.out.println("    (This may take 5-10 minutes for Native Compilation)");

                String imageName = "kasion/" + artifactId + ":latest";

                // Run the Docker command
                runCommand(workingDir, "docker", "build", "-t", imageName, ".");

                System.out.println("✅ [Job " + jobId + "] Docker Image Built Successfully: " + imageName);
                updateStatus(deploymentId, "LIVE");
                // ... (after docker build command) ...

                System.out.println("✅ [Job " + jobId + "] Docker Image Built: " + imageName);

                // --- THE NEW PART: DEPLOY ---
                updateStatus(deploymentId, "DEPLOYING");
                deployContainer(artifactId);
                // ----------------------------

                updateStatus(deploymentId, "LIVE");

            } else {
                System.err.println("❌ [Job " + jobId + "] No pom.xml found.");
                updateStatus(deploymentId, "FAILED");
            }

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus(deploymentId, "ERROR");
        }
    }

    // 🔨 Helper: Runs shell commands (docker build)
    private void runCommand(Path workingDir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        // Stream output to console
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("    [Docker] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }

    // 🧠 Helper: Smarter XML Parser
    private String parseArtifactId(File pomFile) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(pomFile);
        doc.getDocumentElement().normalize();

        var artifacts = doc.getElementsByTagName("artifactId");

        for (int i = 0; i < artifacts.getLength(); i++) {
            String val = artifacts.item(i).getTextContent();
            if (!val.equals("spring-boot-starter-parent")) {
                return val;
            }
        }
        return "my-app";
    }

    private void updateStatus(String id, String status) {
        Optional<Deployment> deploymentOpt = deploymentRepository.findById(id);
        if (deploymentOpt.isPresent()) {
            Deployment deployment = deploymentOpt.get();
            deployment.setStatus(status);
            deploymentRepository.save(deployment);
        }
    }
    // 🚀 NEW: Spins up the container we just built
    private void deployContainer(String artifactId) throws Exception {
        String imageName = "kasion/" + artifactId + ":latest";
        String containerName = artifactId + "-app";

        System.out.println("🚀 [Deploy] Stopping old container (if any)...");
        // Try to stop/remove old container (ignore errors if it doesn't exist)
        try {
            runCommand(Path.of("."), "docker", "rm", "-f", containerName);
        } catch (Exception e) {
            // It's fine, probably didn't exist
        }

        System.out.println("🚀 [Deploy] Starting new container: " + containerName);
        // docker run -d -p 8081:8080 --name spring-petclinic-app kasion/spring-petclinic:latest
        // Note: We map port 8081 on host to 8080 in container
        runCommand(Path.of("."), "docker", "run", "-d",
                "-p", "8081:8080",
                "--name", containerName,
                imageName);

        System.out.println("✅ [Deploy] App is LIVE at: http://localhost:8081");
    }
}