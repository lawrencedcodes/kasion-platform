package io.kasion.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Callable;

@Command(name = "status", description = "Check the status of a deployment")
public class StatusCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The Deployment ID to check")
    private String deploymentId;

    @Override
    public Integer call() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/v1/deployments/" + deploymentId))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("📡 STATUS REPORT: " + response.body());
                return 0;
            } else {
                System.err.println("❌ Deployment not found (404)");
                return 1;
            }
        } catch (Exception e) {
            System.err.println("❌ Could not reach Kasion Brain.");
            return 1;
        }
    }
}