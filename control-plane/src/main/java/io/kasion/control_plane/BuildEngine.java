package io.kasion.control_plane;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BuildEngine {

    private final DeploymentRepository deploymentRepository;
    private final DockerfileGenerator dockerfileGenerator;

    public BuildEngine(DeploymentRepository deploymentRepository, DockerfileGenerator dockerfileGenerator) {
        this.deploymentRepository = deploymentRepository;
        this.dockerfileGenerator = dockerfileGenerator;
    }
    // 🆕 In-Memory Log Storage (deploymentId -> Log Content)
    public static final Map<String, StringBuilder> BUILD_LOGS = new ConcurrentHashMap<>();

    // ... existing constructor ...

    // 🆕 Helper to log to BOTH Console and Memory
    // ✅ CORRECT CODE
    private void log(String deploymentId, String message) {
        System.out.println(message); // <--- Must be System.out.println here!

        // Save to memory for the dashboard
        BUILD_LOGS.computeIfAbsent(deploymentId, k -> new StringBuilder())
                .append(message).append("\n");
    }
    @Async
    public void startBuild(String deploymentId) {
        // 1. Fetch Deployment
        // If your Deployment ID is a String in the DB, change 'Long' to 'String' above and below.
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

        Project project = deployment.getProject();
        String jobId = UUID.randomUUID().toString().substring(0, 8);

        log(deploymentId, "🚀 [Job " + jobId + "] Engine started for: " + project.getName());

        File workspace = null;
        try {
            // 2. Create Workspace (Path -> File)
            // We use Path to create the dir, but convert to File for runCommand
            Path workspacePath = Files.createTempDirectory("kasion-build-" + jobId);
            workspace = workspacePath.toFile();

            log(deploymentId, "📂 [Job " + jobId + "] Workspace created: " + workspace.getAbsolutePath());

            // 3. Clone (Native Git)
            String repoUrl = project.getGithubRepoUrl();
            log(deploymentId, "⬇️ [Job " + jobId + "] Cloning: " + repoUrl);

            // Explicitly passing 'workspace' (File) as the first argument
            runCommand(workspace, deploymentId,"git", "clone", repoUrl, ".");

            log(deploymentId, "✅ [Job " + jobId + "] Code cloned.");

            // 4. Generate Dockerfile
            log(deploymentId, "🧠 [Job " + jobId + "] Generating Standard JVM Strategy...");
            String dockerfileContent = dockerfileGenerator.generateStandardBuild("21");

            // Write Dockerfile: Convert File -> Path for the 'Files.writeString' method
            File dockerfile = new File(workspace, "Dockerfile");
            Files.writeString(dockerfile.toPath(), dockerfileContent);

            log(deploymentId, "📝 [Job " + jobId + "] Dockerfile written to disk.");

            // 5. Build Docker Image
            String imageName = "kasion/" + project.getName().toLowerCase() + ":latest"; // Ensure lowercase for Docker
            log(deploymentId, "🐳 [Job " + jobId + "] Building Image: " + imageName);

            runCommand(workspace, deploymentId, "docker", "build", "-t", imageName, ".");

            // 6. Run It
            // ... (Previous steps 1-5 remain the same) ...

            // ------------------------------------------------------------
            // 🆕 STEP 6: Database & Orchestration
            // ------------------------------------------------------------

            // A. Handle Database (if enabled)
            if (project.isHasDatabase()) {
                log(deploymentId, "🗄️ [Deploy] Ensuring Database is running...");
                String dbContainerName = project.getName().toLowerCase() + "-db";

                // Check if DB is already running (we don't want to kill it and lose data!)
                boolean dbRunning = false;
                try {
                    // Quick hack to check existence: try to inspect it
                    // If this fails, the catch block runs and we create it.
                    runCommand(workspace, deploymentId,"docker", "inspect", dbContainerName);
                    dbRunning = true;
                    log(deploymentId, "   [DB] Database already running. Preserving it.");
                } catch (Exception e) {
                    log(deploymentId, "   [DB] No database found. Creating new one...");
                }

                if (!dbRunning) {
                    // Start Postgres 16
                    // We use the project name to create a unique DB host
                    runCommand(workspace, deploymentId,"docker", "run", "-d",
                            "--name", dbContainerName,
                            "--network", "kasion-net", // ⚠️ Important: We need a shared network
                            "-e", "POSTGRES_USER=" + project.getDbUser(),
                            "-e", "POSTGRES_PASSWORD=" + project.getDbPassword(),
                            "-e", "POSTGRES_DB=" + project.getName().toLowerCase(),
                            "postgres:16-alpine");
                }
            }

            // B. Run the App (with Link to DB)
            log(deploymentId, "🚀 [Deploy] Stopping old app container...");
            try {
                runCommand(workspace, deploymentId,"docker", "rm", "-f", project.getName().toLowerCase() + "-app");
            } catch (Exception ignored) {}

            log(deploymentId, "🚀 [Deploy] Starting new app container...");

            List<String> runCmd = new ArrayList<>();
            runCmd.add("docker");
            runCmd.add("run");
            runCmd.add("-d");
            runCmd.add("--name");
            runCmd.add(project.getName().toLowerCase() + "-app");
            runCmd.add("--network");
            runCmd.add("kasion-net"); // ⚠️ Must match DB network
            runCmd.add("-p");
            runCmd.add("8081:8080");

            // 💉 INJECT MAGIC DATABASE VARIABLES
            if (project.isHasDatabase()) {
                String dbHost = project.getName().toLowerCase() + "-db";
                String dbUrl = "jdbc:postgresql://" + dbHost + ":5432/" + project.getName().toLowerCase();

                runCmd.add("-e");
                runCmd.add("SPRING_DATASOURCE_URL=" + dbUrl);
                runCmd.add("-e");
                runCmd.add("SPRING_DATASOURCE_USERNAME=" + project.getDbUser());
                runCmd.add("-e");
                runCmd.add("SPRING_DATASOURCE_PASSWORD=" + project.getDbPassword());

                // Spring Boot specific tweak: Tell it to update schema automatically
                runCmd.add("-e");
                runCmd.add("SPRING_JPA_HIBERNATE_DDL_AUTO=update");
            }

            runCmd.add(imageName);

            // Execute the dynamic command
            runCommand(workspace, deploymentId, runCmd.toArray(new String[0]));

            log(deploymentId, "✅ [Deploy] LIVE at http://localhost:8081");

            // ... (Status update and Save remains the same) ...

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
    // ⚠️ UPDATED SIGNATURE: Added 'String deploymentId' as the second parameter
    private void runCommand(File workingDir, String deploymentId, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log(deploymentId, "   [Cmd] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
}