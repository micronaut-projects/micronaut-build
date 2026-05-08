package io.micronaut.build;

import io.micronaut.build.problems.MicronautBuildProblems;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
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

import javax.inject.Inject;

public abstract class MavenCentralPublishTask extends DefaultTask {
    private static final Pattern DEPLOYMENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}");

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

    @Inject
    public abstract Problems getProblems();

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
        var sanitizedBody = MicronautBuildProblems.sanitizeDiagnosticText(response.body());

        getLogger().lifecycle("Upload response: {} {}", response.statusCode(), sanitizedBody);

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            var deploymentId = extractDeploymentId(response.body());
            if (deploymentId != null && !deploymentId.isEmpty()) {
                verifyDeploymentStatus(client, deploymentId);
            } else {
                throw deploymentFailure("Could not extract deploymentId from response: " + sanitizedBody, "Maven Central returned a successful upload response without a valid deployment id.");
            }
        } else {
            throw deploymentFailure("Unexpected status code: " + response.statusCode() + " (" + sanitizedBody + ")", "Maven Central returned HTTP " + response.statusCode() + " while uploading the publication bundle.");
        }
    }

    private void verifyDeploymentStatus(HttpClient client, String deploymentId) throws IOException, InterruptedException {
        var statusUrl = buildStatusUrl(deploymentId);
        getLogger().lifecycle("Checking deployment status for {}", deploymentId);
        int maxLookups = 100;
        while (--maxLookups >= 0) {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(statusUrl))
                .header("Authorization", "Bearer " + getBearerToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var sanitizedBody = MicronautBuildProblems.sanitizeDiagnosticText(response.body());

            getLogger().lifecycle("Status check: {} {}", response.statusCode(), sanitizedBody);

            var body = response.body();
            if (response.statusCode() == 200) {
                if (body.contains("\"deploymentState\":\"COMPLETE\"") || body.contains("\"deploymentState\":\"PUBLISHED\"")) {
                    getLogger().lifecycle("Deployment {} completed successfully!", deploymentId);
                    return;
                }
                if (body.contains("\"deploymentState\":\"FAILED\"")) {
                    throw deploymentFailure("Deployment " + deploymentId + " failed: " + sanitizedBody, "Maven Central reported a failed deployment state for deployment " + deploymentId + ".");
                }
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                getLogger().warn("Status check for deployment " + deploymentId + " failed with: " + sanitizedBody + ". This doesn't necessarily mean that deployment failed, please check status on https://central.sonatype.com/publishing");
                break;
            }

            Thread.sleep(30_000);
        }
    }

    private RuntimeException deploymentFailure(String message, String details) {
        return MicronautBuildProblems.throwing(getProblems(), new GradleException(message), MicronautBuildProblems.MAVEN_CENTRAL_DEPLOYMENT_FAILED, spec -> spec
            .contextualLabel("Maven Central deployment failed")
            .details(details)
            .solution("Check the sanitized Maven Central response and verify the publication bundle and deployment status at https://central.sonatype.com/publishing."));
    }

    static String extractDeploymentId(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        var deploymentId = responseBody.trim();
        if (!MicronautBuildProblems.sanitizeDiagnosticText(deploymentId).equals(deploymentId)) {
            return null;
        }
        if (DEPLOYMENT_ID.matcher(deploymentId).matches()) {
            return deploymentId;
        }
        return null;
    }

    static String buildStatusUrl(String deploymentId) {
        return "https://central.sonatype.com/api/v1/publisher/status?id=" + URLEncoder.encode(deploymentId, StandardCharsets.UTF_8);
    }
}
