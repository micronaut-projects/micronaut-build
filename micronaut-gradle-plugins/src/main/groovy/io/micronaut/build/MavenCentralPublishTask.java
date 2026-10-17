package io.micronaut.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

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
import java.util.regex.Pattern;

@DisableCachingByDefault(because = "Publishes artifacts to Maven Central")
public abstract class MavenCentralPublishTask extends DefaultTask {

    private static final Pattern DEPLOYMENT_STATE = Pattern.compile("\"deploymentState\"\\s*:\\s*\"([A-Z_]+)\"");

    public enum PublishingType {
        AUTOMATIC,
        USER_MANAGED
    }

    enum DeploymentStatus {
        IN_PROGRESS,
        VALIDATED,
        PUBLISHED,
        FAILED
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBundle();

    @Input
    public abstract Property<String> getUsername();

    @Input
    public abstract Property<String> getPassword();

    @Input
    @Optional
    @Option(option = "publishing-type", description = "Configures the Maven Central publishing type.")
    public abstract Property<PublishingType> getPublishingType();

    /**
     * The file the deployment id returned by the Publisher API is written to,
     * so that CI can later publish or drop a USER_MANAGED deployment.
     */
    @OutputFile
    public abstract RegularFileProperty getDeploymentIdFile();

    public MavenCentralPublishTask() {
        super();
        setDescription("Publishes a bundle using Maven Central's Publisher API");
        getOutputs().upToDateWhen(t -> false);
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

        var publishingType = getPublishingType().getOrElse(PublishingType.USER_MANAGED);
        var uriBuilder = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=" + publishingType;

        var request = HttpRequest.newBuilder()
            .uri(new URI(uriBuilder))
            .header("Authorization", "Bearer " + getBearerToken())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        getLogger().lifecycle("Upload response: {} {}", response.statusCode(), response.body());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            var deploymentId = response.body() == null ? null : response.body().trim();
            if (deploymentId != null && !deploymentId.isEmpty()) {
                var deploymentIdFile = getDeploymentIdFile().get().getAsFile().toPath();
                Files.createDirectories(deploymentIdFile.getParent());
                Files.writeString(deploymentIdFile, deploymentId, StandardCharsets.UTF_8);
                getLogger().lifecycle("Deployment id {} written to {}", deploymentId, deploymentIdFile);
                verifyDeploymentStatus(client, deploymentId, publishingType);
            } else {
                throw new GradleException("Could not extract deploymentId from response: " + response.body());
            }
        } else {
            throw new GradleException("Unexpected status code: " + response.statusCode() + " (" + response.body() + ")");
        }
    }

    private void verifyDeploymentStatus(HttpClient client, String deploymentId, PublishingType publishingType) throws IOException, InterruptedException {
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
                switch (deploymentStatus(body, publishingType)) {
                    case PUBLISHED -> {
                        getLogger().lifecycle("Deployment {} completed successfully!", deploymentId);
                        return;
                    }
                    case VALIDATED -> {
                        getLogger().lifecycle("Deployment {} validated successfully, awaiting publication on https://central.sonatype.com/publishing", deploymentId);
                        return;
                    }
                    case FAILED -> throw new GradleException("Deployment " + deploymentId + " failed: " + body);
                    case IN_PROGRESS -> {
                        // keep polling
                    }
                }
            } else if (response.statusCode() < 200 || response.statusCode() > 300) {
                getLogger().warn("Status check for deployment " + deploymentId + " failed with: " + body + ". This doesn't necessarily mean that deployment failed, please check status on https://central.sonatype.com/publishing");
                break;
            }

            Thread.sleep(30_000);
        }
    }

    /**
     * Determines the outcome of a deployment from a status response body.
     * A USER_MANAGED deployment is complete once it is VALIDATED, since it
     * then waits for an explicit publish (or drop) request.
     */
    static DeploymentStatus deploymentStatus(String body, PublishingType publishingType) {
        var matcher = DEPLOYMENT_STATE.matcher(body == null ? "" : body);
        if (!matcher.find()) {
            return DeploymentStatus.IN_PROGRESS;
        }
        return switch (matcher.group(1)) {
            case "COMPLETE", "PUBLISHED" -> DeploymentStatus.PUBLISHED;
            case "VALIDATED" -> publishingType == PublishingType.USER_MANAGED ? DeploymentStatus.VALIDATED : DeploymentStatus.IN_PROGRESS;
            case "FAILED" -> DeploymentStatus.FAILED;
            default -> DeploymentStatus.IN_PROGRESS;
        };
    }
}
