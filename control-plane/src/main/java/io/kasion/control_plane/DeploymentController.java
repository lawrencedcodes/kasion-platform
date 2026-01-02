package io.kasion.control_plane;

import io.kasion.control_plane.*;
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
        String projectName = payload.get("projectName");
        System.out.println("⚡ RECEIVED DEPLOY REQUEST FOR: " + projectName);

        // 1. Find or Create Project
        Project project = projectRepository.findByName(projectName)
                .orElseGet(() -> projectRepository.save(new Project(projectName, "unknown-repo")));

        // 2. Create Deployment Record (PENDING)
        Deployment deployment = new Deployment(project, "PENDING");
        deploymentRepository.save(deployment);

        // 3. Trigger Async Build (Fire and Forget)
        buildEngine.startBuild(deployment.getId());

        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "deploymentId", deployment.getId(),
                "message", "The Furnace is heating up."
        ));
    }

    // NEW ENDPOINT: Check Status
    @GetMapping("/deployments/{id}")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        return deploymentRepository.findById(id)
                .map(deployment -> ResponseEntity.ok(Map.of(
                        "id", deployment.getId(),
                        "status", deployment.getStatus()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
