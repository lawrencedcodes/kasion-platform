package io.kasion.control_plane;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    private String id;
    private String name;
    private String ownerId;
    private String githubRepoUrl; // <--- The field exists...
    private String buildStatus;
    private LocalDateTime lastDeployedAt;

    private boolean hasDatabase = false;

    private String dbUser;

    private String dbPassword;

    private int activePort;
    private String currentColor;

    public Project() {}

    public Project(String name, String ownerId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.ownerId = ownerId;
        this.buildStatus = "IDLE";
        this.currentColor = "blue"; // Default to blue
        this.activePort = 8081; // Default to 8081
    }

    // --- GETTERS (Add the missing one here) ---

    public String getId() { return id; }
    public String getName() { return name; }
    public String getOwnerId() { return ownerId; }

    // 🚨 THIS IS THE FIX:
    public String getGithubRepoUrl() { return githubRepoUrl; }

    // --- SETTERS ---
    public void setGithubRepoUrl(String githubRepoUrl) { this.githubRepoUrl = githubRepoUrl; }
    public void setBuildStatus(String buildStatus) { this.buildStatus = buildStatus; }
    public void setLastDeployedAt(LocalDateTime lastDeployedAt) { this.lastDeployedAt = lastDeployedAt; }
    public boolean isHasDatabase() { return hasDatabase; }
    public void setHasDatabase(boolean hasDatabase) { this.hasDatabase = hasDatabase; }

    public String getDbUser() { return dbUser; }
    public void setDbUser(String dbUser) { this.dbUser = dbUser; }

    public String getDbPassword() { return dbPassword; }
    public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }

    public int getActivePort() { return activePort; }
    public void setActivePort(int activePort) { this.activePort = activePort; }

    public String getCurrentColor() { return currentColor; }
    public void setCurrentColor(String currentColor) { this.currentColor = currentColor; }
}