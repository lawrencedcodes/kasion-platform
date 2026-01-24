package io.kasion.control_plane;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;

    public DashboardController(DeploymentRepository deploymentRepository, ProjectRepository projectRepository) {
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/")
    public String showDashboard(Model model) {
        // Fetch all deployments
        List<Deployment> deployments = deploymentRepository.findAll();

        // Add the list to the model so the HTML table can render it
        model.addAttribute("deployments", deployments);

        // FIXED: We calculate the unique project count by getting the Project object first
        long uniqueProjects = deployments.stream()
                .map(d -> d.getProject().getId()) // <--- The Fix
                .distinct()
                .count();

        model.addAttribute("projectCount", uniqueProjects);
        model.addAttribute("projects", projectRepository.findAll());

        return "dashboard";
    }
}