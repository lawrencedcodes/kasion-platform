package io.kasion.control_plane;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    private String status; // PENDING, BUILDING, LIVE, FAILED
    private String commitHash;
    private LocalDateTime createdAt;

    public Deployment() {}

    public Deployment(Project project, String status) {
        this.project = project;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    // Getters/Setters
    public String getId() { return id; }
}
