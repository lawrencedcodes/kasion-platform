package io.kasion.control_plane;

import io.kasion.control_plane.Deployment; // Ensure these match your package structure
import io.kasion.control_plane.Project;
import io.kasion.control_plane.DeploymentRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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
        // 1. Fetch Deployment
        // If your Deployment ID is a String in the DB, change 'Long' to 'String' above and below.
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

        Project project = deployment.getProject();
        String jobId = UUID.randomUUID().toString().substring(0, 8);

        System.out.println("🚀 [Job " + jobId + "] Engine started for: " + project.getName());

        File workspace = null;
        try {
            // 2. Create Workspace (Path -> File)
            // We use Path to create the dir, but convert to File for runCommand
            Path workspacePath = Files.createTempDirectory("kasion-build-" + jobId);
            workspace = workspacePath.toFile();

            System.out.println("📂 [Job " + jobId + "] Workspace created: " + workspace.getAbsolutePath());

            // 3. Clone (Native Git)
            String repoUrl = project.getGithubRepoUrl();
            System.out.println("⬇️ [Job " + jobId + "] Cloning: " + repoUrl);

            // Explicitly passing 'workspace' (File) as the first argument
            runCommand(workspace, "git", "clone", repoUrl, ".");

            System.out.println("✅ [Job " + jobId + "] Code cloned.");

            // 4. Generate Dockerfile
            System.out.println("🧠 [Job " + jobId + "] Generating Standard JVM Strategy...");
            String dockerfileContent = dockerfileGenerator.generateStandardBuild("21");

            // Write Dockerfile: Convert File -> Path for the 'Files.writeString' method
            File dockerfile = new File(workspace, "Dockerfile");
            Files.writeString(dockerfile.toPath(), dockerfileContent);

            System.out.println("📝 [Job " + jobId + "] Dockerfile written to disk.");

            // 5. Build Docker Image
            String imageName = "kasion/" + project.getName().toLowerCase() + ":latest"; // Ensure lowercase for Docker
            System.out.println("🐳 [Job " + jobId + "] Building Image: " + imageName);

            runCommand(workspace, "docker", "build", "-t", imageName, ".");

            // 6. Run It
            System.out.println("🚀 [Deploy] Stopping old container...");
            try {
                // Ignore errors here (if container doesn't exist yet)
                runCommand(workspace, "docker", "rm", "-f", project.getName().toLowerCase() + "-app");
            } catch (Exception ignored) {}

            System.out.println("🚀 [Deploy] Starting new container...");
            // Run on port 8081
            runCommand(workspace, "docker", "run", "-d",
                    "--name", project.getName().toLowerCase() + "-app",
                    "-p", "8081:8080",
                    imageName);

            System.out.println("✅ [Deploy] LIVE at http://localhost:8081");

            deployment.setStatus("LIVE");
            deploymentRepository.save(deployment);

        } catch (Exception e) {
            System.err.println("❌ [Job " + jobId + "] Build Failed!");
            e.printStackTrace();
            deployment.setStatus("FAILED");
            deploymentRepository.save(deployment);
        }
        // Ideally delete the workspace here, but keeping it for debugging
    }

    /**
     * Helper method to run shell commands in a specific directory.
     * Takes a File object as the directory.
     */
    private void runCommand(File workingDir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir); // This expects a File, which is why we convert Path to File earlier
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("   [Cmd] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
}