package io.kasion.cli;

import picocli.CommandLine.Command;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "push", description = "Deploy the current project to the Kasion Grid")
public class PushCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        String projectName = Paths.get("").toAbsolutePath().getFileName().toString();
        System.out.println("🚀 Detecting Spring Boot project: " + projectName);
        System.out.println("🔥 Initiating Kasion Furnace...");

        // Connect to the Brain (Control Plane)
        String jsonPayload = String.format("{\"projectName\": \"%s\"}", projectName);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/v1/deploy"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("✅ [SUCCESS] " + response.body());
                return 0;
            } else {
                System.err.println("❌ [ERROR] Server rejected request: " + response.statusCode());
                return 1;
            }
        } catch (Exception e) {
            System.err.println("❌ [ERROR] Could not connect to Kasion Brain. Is it running?");
            return 1;
        }
    }
}
