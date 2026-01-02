package io.kasion.control_plane;

import io.kasion.control_plane.Deployment;
import io.kasion.control_plane.DeploymentRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class BuildEngine {

    private final DeploymentRepository deploymentRepository;

    public BuildEngine(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    @Async // <--- This runs in a separate thread!
    public void startBuild(String deploymentId) {
        try {
            // 1. Simulate "Queued" time
            System.out.println("⏳ [Job " + deploymentId + "] Waiting for worker...");
            Thread.sleep(3000); // 3 seconds

            // 2. Update to BUILDING
            updateStatus(deploymentId, "BUILDING");
            System.out.println("🔨 [Job " + deploymentId + "] Compiling Native Image...");
            Thread.sleep(5000); // 5 seconds (The heavy lifting)

            // 3. Update to LIVE
            updateStatus(deploymentId, "LIVE");
            System.out.println("✅ [Job " + deploymentId + "] Deployment Successful!");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateStatus(String id, String status) {
        Optional<Deployment> deploymentOpt = deploymentRepository.findById(id);
        if (deploymentOpt.isPresent()) {
            Deployment deployment = deploymentOpt.get();
            // We need to add a setter for status in your Entity if it doesn't exist!
            // Assuming you have one, or we access the field directly if public (better to use setter)
            deployment.setStatus(status);
            deploymentRepository.save(deployment);
        }
    }
}
