package io.kasion.control_plane;

import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.HashMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1") // This stays! All methods here will start with /api/v1
public class DeploymentController {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final BuildEngine buildEngine;

    public DeploymentController(ProjectRepository projectRepository,
                                DeploymentRepository deploymentRepository,
                                BuildEngine buildEngine) {
        this.projectRepository = projectRepository;
        this.deploymentRepository = deploymentRepository;
        this.buildEngine = buildEngine;
    }

    @PostMapping("/deploy")
    public ResponseEntity<?> deploy(@RequestBody Map<String, String> payload) {
        String repoUrl = payload.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Repo URL is required"));
        }

        String projectName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");
        System.out.println("⚡ RECEIVED DEPLOY REQUEST: " + projectName + " [" + repoUrl + "]");

        Project project = projectRepository.findByName(projectName)
                .orElseGet(() -> {
                    Project newProject = new Project(projectName, "web-user");
                    newProject.setGithubRepoUrl(repoUrl);
                    return projectRepository.save(newProject);
                });

        if (!project.getGithubRepoUrl().equals(repoUrl)) {
            project.setGithubRepoUrl(repoUrl);
            projectRepository.save(project);
        }

        Deployment deployment = new Deployment(project, "PENDING");
        deploymentRepository.save(deployment);

        buildEngine.startBuild(deployment.getId());

        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "deploymentId", deployment.getId(),
                "project", projectName
        ));
    }

    @GetMapping("/deployments/{id}")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        return deploymentRepository.findById(id)
                .map(deployment -> ResponseEntity.ok(Map.of(
                        "id", deployment.getId(),
                        "status", deployment.getStatus()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ✅ API Only: Returns raw text.
    // This will be accessible at: /api/v1/api/logs/{id}  <-- Note the double 'api' if we aren't careful
    // Let's make it /api/v1/logs/{id} by removing the extra /api prefix here
    @GetMapping("/logs/{id}")
    public String getLogs(@PathVariable String id) {
        StringBuilder logs = BuildEngine.BUILD_LOGS.get(id);
        return logs != null ? logs.toString() : "Waiting for logs...";
    }

    // 🆕 The "X-Ray" Endpoint
    @GetMapping("/stats/{id}")
    public Map<String, Object> getAppStats(@PathVariable String id) {
        String baseUrl = "http://localhost:8081/actuator";
        Map<String, Object> stats = new HashMap<>();
        RestClient client = RestClient.create();

        try {
            String healthJson = client.get().uri(baseUrl + "/health").retrieve().body(String.class);
            stats.put("status", healthJson.contains("UP") ? "UP" : "DOWN");

            Map<String, Object> memData = client.get().uri(baseUrl + "/metrics/jvm.memory.used").retrieve().body(Map.class);
            var measurements = (java.util.List<Map<String, Object>>) memData.get("measurements");
            Double bytes = (Double) measurements.get(0).get("value");
            long mb = Math.round(bytes / 1024 / 1024);
            stats.put("memory", mb + " MB");
        } catch (Exception e) {
            stats.put("status", "UNREACHABLE");
            stats.put("memory", "0 MB");
        }
        return stats;
    }
}
