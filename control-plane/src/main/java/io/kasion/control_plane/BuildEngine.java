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

    public BuildEngine(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    @Async
    public void startBuild(String deploymentId) {
        String jobId = deploymentId.substring(0, 8);
        System.out.println("🚀 [Job " + jobId + "] Engine started. Preparing workspace...");

        try {
            // 1. Create a temporary folder (The "Clean Room")
            Path workingDir = Files.createTempDirectory("kasion-build-" + jobId);
            System.out.println("📂 [Job " + jobId + "] Workspace: " + workingDir.toString());

            updateStatus(deploymentId, "CLONING");

            // 2. Clone the Repo (Hardcoded to Spring PetClinic for this test)
            String demoRepoUrl = "https://github.com/spring-projects/spring-petclinic.git";
            System.out.println("⬇️ [Job " + jobId + "] Cloning " + demoRepoUrl + "...");

            try (Git git = Git.cloneRepository()
                    .setURI(demoRepoUrl)
                    .setDirectory(workingDir.toFile())
                    .call()) {
                System.out.println("✅ [Job " + jobId + "] Clone Complete.");
            }

            // 3. Parse the POM.xml
            updateStatus(deploymentId, "ANALYZING");
            File pomFile = new File(workingDir.toFile(), "pom.xml");

            if (pomFile.exists()) {
                System.out.println("🔎 [Job " + jobId + "] Found pom.xml. Parsing...");
                String artifactId = parseArtifactId(pomFile);
                System.out.println("📦 [Job " + jobId + "] Detected Artifact: " + artifactId);

                // Pretend we are compiling it (Maven takes too long for a demo)
                updateStatus(deploymentId, "BUILDING");
                Thread.sleep(2000);

                updateStatus(deploymentId, "LIVE");
                System.out.println("🎉 [Job " + jobId + "] Successfully deployed: " + artifactId);
            } else {
                System.err.println("❌ [Job " + jobId + "] No pom.xml found!");
                updateStatus(deploymentId, "FAILED");
            }

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus(deploymentId, "ERROR");
        }
    }

    // Helper: Reads the <artifactId> tag from pom.xml
    private String parseArtifactId(File pomFile) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(pomFile);
        doc.getDocumentElement().normalize();
        return doc.getElementsByTagName("artifactId").item(0).getTextContent();
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