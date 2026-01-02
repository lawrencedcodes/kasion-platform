package io.kasion.control_plane;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class DeploymentController {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;

    public DeploymentController(ProjectRepository projectRepository, DeploymentRepository deploymentRepository) {
        this.projectRepository = projectRepository;
        this.deploymentRepository = deploymentRepository;
    }

    @PostMapping("/deploy")
    public ResponseEntity<?> deploy(@RequestBody Map<String, String> payload) {
        String projectName = payload.get("projectName");

        System.out.println("⚡ RECEIVED DEPLOY REQUEST FOR: " + projectName);

        // 1. Find or Create Project
        Project project = projectRepository.findByName(projectName)
                .orElseGet(() -> projectRepository.save(new Project(projectName, "unknown-repo")));

        // 2. Create Deployment Record
        Deployment deployment = new Deployment(project, "PENDING");
        deploymentRepository.save(deployment);

        // 3. Mock Triggering the "Furnace"
        System.out.println("🔥 IGNITING FURNACE FOR DEPLOYMENT: " + deployment.getId());

        return ResponseEntity.ok(Map.of(
                "status", "queued",
                "deploymentId", deployment.getId(),
                "message", "The Furnace is heating up."
        ));
    }
}
