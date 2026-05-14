package io.micronaut.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

public abstract class MavenCentralPublishTask extends DefaultTask {

    public enum PublishingType {
        AUTOMATIC,
        USER_MANAGED
    }

    @InputFile
    public abstract RegularFileProperty getBundle();

    @Input
    public abstract Property<String> getUsername();

    @Input
    public abstract Property<String> getPassword();

    @Input
    @Optional
    @Option(option = "publishing-type", description = "Configures the Maven Central publishing type.")
    public abstract Property<PublishingType> getPublishingType();

    public MavenCentralPublishTask() {
        super();
        setDescription("Publishes a bundle using Maven Central's Publisher API");
    }

    private String getBearerToken() {
        var usernamePassword = String.format("%s:%s", getUsername().get(), getPassword().get());
        return Base64.getEncoder()
            .encodeToString(usernamePassword.getBytes(StandardCharsets.UTF_8));
    }

    @TaskAction
    public void uploadBundle() throws URISyntaxException, IOException, InterruptedException {
        var client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

        var file = getBundle().get().getAsFile().toPath();
        var fileName = file.getFileName().toString();
        var fileBytes = Files.readAllBytes(file);

        var boundary = UUID.randomUUID().toString();

        var bodyBuilder = "--" + boundary + "\r\n" +
                          "Content-Disposition: form-data; name=\"bundle\"; filename=\"" + fileName + "\"\r\n" +
                          "Content-Type: application/octet-stream\r\n\r\n";

        var prefix = bodyBuilder.getBytes(StandardCharsets.UTF_8);
        var suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        var requestBody = ByteBuffer.allocate(prefix.length + fileBytes.length + suffix.length)
            .put(prefix)
            .put(fileBytes)
            .put(suffix)
            .array();

        var uriBuilder = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=" + getPublishingType().getOrElse(PublishingType.USER_MANAGED);

        var request = HttpRequest.newBuilder()
            .uri(new URI(uriBuilder))
            .header("Authorization", "Bearer " + getBearerToken())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        getLogger().lifecycle("Upload response: {} {}", response.statusCode(), response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            var deploymentId = response.body();
            if (deploymentId != null && !deploymentId.isEmpty()) {
                verifyDeploymentStatus(client, deploymentId);
            } else {
                throw new GradleException("Could not extract deploymentId from response: " + response.body());
            }
        } else {
            throw new GradleException("Unexpected status code: " + response.statusCode() + " (" + response.body() + ")");
        }
    }

    private void verifyDeploymentStatus(HttpClient client, String deploymentId) throws IOException, InterruptedException {
        var statusUrl = "https://central.sonatype.com/api/v1/publisher/status?id=" + deploymentId;
        getLogger().lifecycle("Checking deployment status for {}", deploymentId);
        int maxLookups = 100;
        while (--maxLookups >= 0) {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(statusUrl))
                .header("Authorization", "Bearer " + getBearerToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            getLogger().lifecycle("Status check: {} {}", response.statusCode(), response.body());

            var body = response.body();
            if (response.statusCode() == 200) {
                if (body.contains("\"deploymentState\":\"COMPLETE\"") || body.contains("\"deploymentState\":\"PUBLISHED\"")) {
                    getLogger().lifecycle("Deployment {} completed successfully!", deploymentId);
                    return;
                }
                if (body.contains("\"deploymentState\":\"FAILED\"")) {
                    throw new GradleException("Deployment " + deploymentId + " failed: " + body);
                }
            } else if (response.statusCode() < 200 || response.statusCode() > 300) {
                getLogger().warn("Status check for deployment " + deploymentId + " failed with: " + body + ". This doesn't necessarily mean that deployment failed, please check status on https://central.sonatype.com/publishing");
                break;
            }

            Thread.sleep(30_000);
        }
    }
}
