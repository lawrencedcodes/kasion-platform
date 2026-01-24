package io.kasion.control_plane;

import io.kasion.control_plane.Project;
import io.kasion.control_plane.ProjectRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
public class ProjectController {

    private final ProjectRepository projectRepository;

    public ProjectController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @PostMapping("/projects/{id}/database")
    public String addDatabase(@PathVariable String id) { // ⚠️ If your Project ID is Long, change String to Long here
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found: " + id));

        // Only generate if not already active to prevent overwriting passwords
        if (!project.isHasDatabase()) {
            project.setHasDatabase(true);
            project.setDbUser("postgres");
            // Generate a secure random password for this specific app
            project.setDbPassword(UUID.randomUUID().toString());

            projectRepository.save(project);
            System.out.println("✅ [Project] Database enabled for " + project.getName());
        }

        // Redirect back to the main dashboard so the user sees the change
        return "redirect:/";
    }
}
