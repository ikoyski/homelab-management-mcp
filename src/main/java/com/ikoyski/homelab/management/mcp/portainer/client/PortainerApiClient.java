package com.ikoyski.homelab.management.mcp.portainer.client;

import java.io.IOException;
import tools.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fixed-operation Portainer API client. Callers cannot supply paths, URLs or headers.
 * Portainer endpoint and container IDs are accepted only as internal values discovered
 * from Portainer responses; they are never part of the inbound API.
 */
@Component
@ConditionalOnProperty(prefix = "homelab.docker.portainer", name = "base-url")
public class PortainerApiClient {

    private static final int MAX_LOG_RESPONSE_BYTES = 256 * 1024;

    private final RestClient restClient;

    public PortainerApiClient(RestClient portainerRestClient) {
        this.restClient = portainerRestClient;
    }

    public JsonNode listEndpoints() {
        return get("/api/endpoints");
    }

    public JsonNode getDockerInfo(int discoveredEndpointId) {
        return getEndpointResource(discoveredEndpointId, "info");
    }

    public JsonNode listContainers(int discoveredEndpointId) {
        return restClient.get()
                .uri("/api/endpoints/{id}/docker/containers/json?all=true", requireEndpointId(discoveredEndpointId))
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode inspectContainer(int discoveredEndpointId, String discoveredContainerId) {
        int endpointId = requireEndpointId(discoveredEndpointId);
        if (discoveredContainerId == null || !discoveredContainerId.matches("[a-fA-F0-9]{12,64}")) {
            throw new IllegalArgumentException("Discovered Docker container ID is invalid");
        }
        return restClient.get()
                .uri("/api/endpoints/{id}/docker/containers/{containerId}/json",
                        endpointId, discoveredContainerId)
                .retrieve()
                .body(JsonNode.class);
    }

    /**
     * Retrieves a bounded, non-following log snapshot for a container discovered by this
     * adapter. The caller cannot supply an API path, query string, or arbitrary container ID.
     */
    public DockerLogPayload getContainerLogs(int discoveredEndpointId, String discoveredContainerId, int tail) {
        int endpointId = requireEndpointId(discoveredEndpointId);
        if (discoveredContainerId == null || !discoveredContainerId.matches("[a-fA-F0-9]{12,64}")) {
            throw new IllegalArgumentException("Discovered Docker container ID is invalid");
        }
        if (tail < 1 || tail > 200) {
            throw new IllegalArgumentException("tail must be between 1 and 200");
        }

        return restClient.get()
                .uri("/api/endpoints/{id}/docker/containers/{containerId}/logs"
                                + "?stdout=true&stderr=true&timestamps=true&follow=false&tail={tail}",
                        endpointId, discoveredContainerId, tail)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status < 200 || status >= 300) {
                        throw new DockerLogRequestException(status);
                    }
                    byte[] received = response.getBody().readNBytes(MAX_LOG_RESPONSE_BYTES + 1);
                    boolean truncated = received.length > MAX_LOG_RESPONSE_BYTES;
                    byte[] bounded = truncated
                            ? java.util.Arrays.copyOf(received, MAX_LOG_RESPONSE_BYTES)
                            : received;
                    return new DockerLogPayload(bounded, truncated);
                });
    }

    private JsonNode getEndpointResource(int endpointId, String resource) {
        int validId = requireEndpointId(endpointId);
        return get("/api/endpoints/" + validId + "/docker/" + resource);
    }

    private JsonNode get(String fixedPath) {
        return restClient.get().uri(fixedPath).retrieve().body(JsonNode.class);
    }

    private int requireEndpointId(int endpointId) {
        if (endpointId <= 0) {
            throw new IllegalArgumentException("Discovered Portainer endpoint ID must be positive");
        }
        return endpointId;
    }

    /** Status-only exception: upstream response bodies are deliberately not retained. */
    public static final class DockerLogRequestException extends RuntimeException {
        private final int statusCode;

        public DockerLogRequestException(int statusCode) {
            super("Docker log request failed with HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
