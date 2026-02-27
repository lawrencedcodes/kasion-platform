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

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final DockerfileGenerator dockerfileGenerator;
    private final LogBroadcaster logBroadcaster;

    public BuildEngine(ProjectRepository projectRepository, DeploymentRepository deploymentRepository, DockerfileGenerator dockerfileGenerator, LogBroadcaster logBroadcaster) {
        this.projectRepository = projectRepository;
        this.deploymentRepository = deploymentRepository;
        this.dockerfileGenerator = dockerfileGenerator;
        this.logBroadcaster = logBroadcaster;
    }

    // 🆕 Helper to log to BOTH Console and WebSocket
    private void log(String deploymentId, String message) {
        System.out.println(message);
        logBroadcaster.broadcast(deploymentId, message);
    }
    @Async
    public void startBuild(String deploymentId) {
        // 1. Fetch Deployment
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

        Project project = deployment.getProject();
        String jobId = UUID.randomUUID().toString().substring(0, 8);

        log(deploymentId, "🚀 [Job " + jobId + "] Engine started for: " + project.getName());

        File workspace = null;
        try {
            Path workspacePath = Files.createTempDirectory("kasion-build-" + jobId);
            workspace = workspacePath.toFile();

            log(deploymentId, "📂 [Job " + jobId + "] Workspace created: " + workspace.getAbsolutePath());

            String repoUrl = project.getGithubRepoUrl();
            log(deploymentId, "⬇️ [Job " + jobId + "] Cloning: " + repoUrl);
            runCommand(workspace, deploymentId,"git", "clone", repoUrl, ".");
            log(deploymentId, "✅ [Job " + jobId + "] Code cloned.");

            boolean hasWrapper = new File(workspace, "mvnw").exists();
            if (hasWrapper) {
                 log(deploymentId, "🧠 [Job " + jobId + "] Found 'mvnw'. Using Project Wrapper.");
            } else {
                 log(deploymentId, "🧠 [Job " + jobId + "] No 'mvnw' found. Using System Maven 3.9.");
            }
            String dockerfileContent = dockerfileGenerator.generateStandardBuild("21", hasWrapper);
            File dockerfile = new File(workspace, "Dockerfile");
            Files.writeString(dockerfile.toPath(), dockerfileContent);
            log(deploymentId, "📝 [Job " + jobId + "] Dockerfile written to disk.");

            String imageName = "kasion/" + project.getName().toLowerCase() + ":" + deployment.getId();
            log(deploymentId, "🐳 [Job " + jobId + "] Building Image: " + imageName);
            runCommand(workspace, deploymentId, "docker", "build", "-t", imageName, ".");

            // NEW: Database Provisioning Logic
            if (project.isHasDatabase()) {
                log(deploymentId, "🔑 [Database] Provisioning requested for " + project.getName());
                String dbContainerName = project.getName().toLowerCase() + "-db";

                // Check if a DB container already exists for this project
                // A simple check is to inspect for a container with the name
                try {
                    runCommand(new File("."), deploymentId, "docker", "inspect", dbContainerName);
                    log(deploymentId, "💡 [Database] Container '" + dbContainerName + "' already exists. Skipping creation.");
                } catch (Exception e) {
                    // This is expected if the container doesn't exist
                    log(deploymentId, "🔎 [Database] No existing container found. Creating new Postgres database...");

                    String dbUser = "kasion_user";
                    String dbPassword = UUID.randomUUID().toString();

                    project.setDbUser(dbUser);
                    project.setDbPassword(dbPassword);
                    projectRepository.save(project);
                    log(deploymentId, "🔐 [Database] Credentials generated and saved.");

                    runCommand(new File("."), deploymentId,
                            "docker", "run", "-d",
                            "--name", dbContainerName,
                            "--network", "kasion-net",
                            "-e", "POSTGRES_USER=" + dbUser,
                            "-e", "POSTGRES_PASSWORD=" + dbPassword,
                            "-e", "POSTGRES_DB=" + project.getName().toLowerCase(),
                            "--restart", "always",
                            "-m", "256m",
                            "postgres:15-alpine"
                    );
                    log(deploymentId, "✅ [Database] Postgres container started successfully!");
                }
            }


            // Blue-Green Deployment Logic
            String currentColor = project.getCurrentColor();
            String nextColor = "blue".equals(currentColor) ? "green" : "blue";
            int nextPort = project.getActivePort() == 8081 ? 8082 : 8081;

            log(deploymentId, "🎨 [Deploy] Current color: " + currentColor + ". Deploying to " + nextColor + " on port " + nextPort);

            // Start the new container
            String newContainerName = project.getName().toLowerCase() + "-app-" + nextColor;
            log(deploymentId, "🚀 [Deploy] Starting new container: " + newContainerName);
            List<String> runCmd = new ArrayList<>();
            runCmd.add("docker");
            runCmd.add("run");
            runCmd.add("-d");
            runCmd.add("--name");
            runCmd.add(newContainerName);
            runCmd.add("--network");
            runCmd.add("kasion-net");
            runCmd.add("-p");
            runCmd.add(nextPort + ":8080");

            if (project.isHasDatabase()) {
                String dbHost = project.getName().toLowerCase() + "-db";
                String dbUrl = "jdbc:postgresql://" + dbHost + ":5432/" + project.getName().toLowerCase();

                runCmd.add("-e");
                runCmd.add("SPRING_DATASOURCE_URL=" + dbUrl);
                runCmd.add("-e");
                runCmd.add("SPRING_DATASOURCE_USERNAME=" + project.getDbUser());
                runCmd.add("-e");
                runCmd.add("SPRING_DATASOURCE_PASSWORD=" + project.getDbPassword());
                runCmd.add("-e");
                runCmd.add("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*");
                runCmd.add("-e");
                runCmd.add("SPRING_JPA_HIBERNATE_DDL_AUTO=update");
            }

            runCmd.add(imageName);
            runCommand(workspace, deploymentId, runCmd.toArray(new String[0]));

            log(deploymentId, "🔬 [Deploy] Health check on new container...");
            boolean isHealthy = false;
            Instant deadline = Instant.now().plusSeconds(120); // 2 minute timeout
            RestClient restClient = RestClient.create();

            while (Instant.now().isBefore(deadline)) {
                try {
                    String healthUrl = "http://localhost:" + nextPort + "/actuator/health";
                    log(deploymentId, "   [Health] Pinging " + healthUrl);
                    String response = restClient.get().uri(healthUrl).retrieve().body(String.class);
                    if (response != null && response.contains("\"status\":\"UP\"")) {
                        isHealthy = true;
                        log(deploymentId, "✅ [Health] Container is UP and healthy!");
                        break;
                    }
                } catch (Exception e) {
                    log(deploymentId, "   [Health] Retrying... container not ready yet.");
                }
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }

            if (!isHealthy) {
                throw new RuntimeException("Health check failed: Container did not become healthy within 2 minutes.");
            }

            log(deploymentId, "🔄 [Deploy] Updating Nginx configuration to point to port " + nextPort);
            String nginxConfig = "server { listen 80; location / { proxy_pass http://host.docker.internal:" + nextPort + "; } }";
            Files.writeString(Path.of("control-plane/nginx/default.conf"), nginxConfig);

            log(deploymentId, "🔃 [Deploy] Reloading Nginx...");
            runCommand(new File("."), deploymentId, "docker-compose", "exec", "nginx", "nginx", "-s", "reload");

            log(deploymentId, "✅ [Deploy] LIVE at http://localhost");

            String oldContainerName = project.getName().toLowerCase() + "-app-" + currentColor;
            log(deploymentId, "🛑 [Deploy] Stopping old container: " + oldContainerName);
            try {
                runCommand(workspace, deploymentId,"docker", "rm", "-f", oldContainerName);
            } catch (Exception ignored) {}

            project.setCurrentColor(nextColor);
            project.setActivePort(nextPort);
            projectRepository.save(project);

            Deployment freshDeployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new RuntimeException("Deployment vanished!"));

            freshDeployment.setStatus("LIVE");
            deploymentRepository.save(freshDeployment);

        } catch (Exception e) {
            System.err.println("❌ [Job " + jobId + "] Build Failed!");
            e.printStackTrace();
            deployment.setStatus("FAILED");
            deploymentRepository.save(deployment);
        }
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