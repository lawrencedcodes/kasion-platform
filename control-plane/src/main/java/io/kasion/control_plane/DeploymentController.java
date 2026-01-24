package io.kasion.control_plane;

import io.kasion.control_plane.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DeploymentController {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final BuildEngine buildEngine; // <--- Inject the worker

    public DeploymentController(ProjectRepository projectRepository,
                                DeploymentRepository deploymentRepository,
                                BuildEngine buildEngine) {
        this.projectRepository = projectRepository;
        this.deploymentRepository = deploymentRepository;
        this.buildEngine = buildEngine;
    }

    @PostMapping("/deploy")
    public ResponseEntity<?> deploy(@RequestBody Map<String, String> payload) {
        // 1. Get the inputs from the UI
        String repoUrl = payload.get("repoUrl");

        // Fail if no URL is provided
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Repo URL is required"));
        }

        // 2. Auto-generate a Project Name from the URL
        // (e.g., "https://github.com/user/my-app.git" -> "my-app")
        String projectName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1).replace(".git", "");

        System.out.println("⚡ RECEIVED DEPLOY REQUEST: " + projectName + " [" + repoUrl + "]");

        // 3. Find or Create Project
        Project project = projectRepository.findByName(projectName)
                .orElseGet(() -> {
                    Project newProject = new Project(projectName, "web-user");
                    newProject.setGithubRepoUrl(repoUrl); // <--- Save the custom URL!
                    return projectRepository.save(newProject);
                });

        // ensure URL is updated if project already existed
        if (!project.getGithubRepoUrl().equals(repoUrl)) {
            project.setGithubRepoUrl(repoUrl);
            projectRepository.save(project);
        }

        // 4. Create Deployment Record
        Deployment deployment = new Deployment(project, "PENDING");
        deploymentRepository.save(deployment);

        // 5. Trigger Async Build
        buildEngine.startBuild(deployment.getId());

        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "deploymentId", deployment.getId(),
                "project", projectName
        ));
    }

    // Check Status
    @GetMapping("/deployments/{id}")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        return deploymentRepository.findById(id)
                .map(deployment -> ResponseEntity.ok(Map.of(
                        "id", deployment.getId(),
                        "status", deployment.getStatus()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/api/logs/{id}")
    public String getLogs(@PathVariable String id) {
        StringBuilder logs = BuildEngine.BUILD_LOGS.get(id);
        return logs != null ? logs.toString() : "Waiting for logs...";
    }

}
