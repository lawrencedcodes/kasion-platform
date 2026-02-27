package io.kasion.control_plane;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/v1/jmx")
public class JmxProxyController {

    private final ProjectRepository projectRepository;
    private final RestClient restClient;

    public JmxProxyController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        this.restClient = RestClient.create();
    }

    @GetMapping("/{projectId}/{*jolokiaPath}")
    public ResponseEntity<String> proxyJmxRequest(@PathVariable String projectId,
                                                  @PathVariable String jolokiaPath) {
        return projectRepository.findById(projectId)
                .map(project -> {
                    int port = project.getActivePort(); // This is the public port, not the internal Jolokia port.
                    // The Jolokia agent exposes itself on port 8778 internally within the container.
                    // We need to route to the *application container* (which has Jolokia) then internally to 8778.
                    // For now, we'll assume the Jolokia agent is accessible directly on the application container's network interface.
                    // The Jolokia agent is bound to 0.0.0.0, so it should be reachable.

                    // Construct the internal URL to the Jolokia agent.
                    // The application container name is <project_name>-app-<color>
                    String appContainerName = project.getName().toLowerCase() + "-app-" + project.getCurrentColor();
                    String jolokiaUrl = "http://" + appContainerName + ":8778/jolokia/" + jolokiaPath;

                    try {
                        String response = restClient.get().uri(jolokiaUrl).retrieve().body(String.class);
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        return ResponseEntity.status(500).body("Error proxying JMX request: " + e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
