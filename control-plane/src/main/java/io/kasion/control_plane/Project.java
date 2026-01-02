package io.kasion.control_plane;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String ownerId; // For future auth
    private String githubRepoUrl;
    private String buildStatus; // IDLE, BUILDING, DEPLOYED
    private LocalDateTime lastDeployedAt;

    // Standard Getters and Setters (Omitted for brevity, generate them in IDE)
    // Or use Lombok @Data if you added it.

    public Project() {}

    public Project(String name, String githubRepoUrl) {
        this.name = name;
        this.githubRepoUrl = githubRepoUrl;
        this.buildStatus = "IDLE";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setBuildStatus(String status) { this.buildStatus = status; }
}
