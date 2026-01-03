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

            // 2. Clone (Still hardcoded to PetClinic for the demo)
            String demoRepoUrl = "https://github.com/spring-projects/spring-petclinic.git";
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
                // FIXED: Uses the smarter parser now
                String artifactId = parseArtifactId(pomFile);
                System.out.println("📦 [Job " + jobId + "] Identified App: " + artifactId);

                System.out.println("🧠 [Job " + jobId + "] Generating Native Image Strategy...");
                // Note: Generates instructions for Java 17/21
                String dockerfileContent = dockerfileGenerator.generateNativeBuild(artifactId, "21");

                // Write the Dockerfile to disk
                Files.writeString(workingDir.resolve("Dockerfile"), dockerfileContent);
                System.out.println("📝 [Job " + jobId + "] Dockerfile written to disk.");

                // --- THE NEW PART: EXECUTION ---
                updateStatus(deploymentId, "BUILDING_IMAGE");
                System.out.println("🐳 [Job " + jobId + "] Sending build context to Docker Daemon...");
                System.out.println("    (This may take 5-10 minutes for Native Compilation)");

                // The Command: docker build -t kasion/spring-petclinic:latest .
                String imageName = "kasion/" + artifactId + ":latest";

                // actually run 'docker build'
                runCommand(workingDir, "docker", "build", "-t", imageName, ".");

                System.out.println("✅ [Job " + jobId + "] Docker Image Built Successfully: " + imageName);
                updateStatus(deploymentId, "LIVE");
                // -------------------------------

            } else {
                System.err.println("❌ [Job " + jobId + "] No pom.xml found.");
                updateStatus(deploymentId, "FAILED");
            }

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus(deploymentId, "ERROR");
        }
    }

    // 🔨 Helper: Runs a shell command in a specific folder
    private void runCommand(Path workingDir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true); // Merge error logs with standard logs

        Process process = builder.start();

        // Stream the logs to the console so we can watch the build
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

    // 🧠 Smarter Parser: Skips the parent definition
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
        return "my-app"; // Fallback
    }

    private void updateStatus(String id, String status) {
        Optional<Deployment> deploymentOpt = deploymentRepository.findById(id);
        if (deploymentOpt.isPresent()) {
            Deployment deployment = deploymentOpt.get();
            deployment.setStatus(status);
            deploymentRepository.save(deployment);
        }
    }
}