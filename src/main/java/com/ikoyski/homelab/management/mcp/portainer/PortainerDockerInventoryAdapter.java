package com.ikoyski.homelab.management.mcp.portainer;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryProvider;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainer;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerHost;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerNetwork;
import com.ikoyski.homelab.management.mcp.inventory.model.PublishedPort;
import com.ikoyski.homelab.management.mcp.portainer.config.PortainerProperties;
import com.ikoyski.homelab.management.mcp.portainer.discovery.PortainerEndpointResolver;
import com.ikoyski.homelab.management.mcp.portainer.discovery.PortainerEndpointResolver.EndpointResolution;
import com.ikoyski.homelab.management.mcp.portainer.discovery.PortainerEndpointResolver.Status;
import com.ikoyski.homelab.management.mcp.portainer.client.PortainerApiClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Portainer infrastructure adapter. It translates fixed Portainer GET operations
 * into application-owned Docker inventory models and never exposes raw upstream JSON.
 */
@Component
@ConditionalOnProperty(prefix = "homelab.docker.portainer", name = "base-url")
public class PortainerDockerInventoryAdapter implements DockerInventoryProvider {
    private static final Logger log = LoggerFactory.getLogger(PortainerDockerInventoryAdapter.class);
    private static final Pattern HEALTH_PATTERN = Pattern.compile("\\b(healthy|unhealthy|starting)\\b",
            Pattern.CASE_INSENSITIVE);

    private final PortainerApiClient client;
    private final PortainerEndpointResolver resolver;
    private final PortainerProperties properties;

    public PortainerDockerInventoryAdapter(PortainerApiClient client,
            PortainerEndpointResolver resolver, PortainerProperties properties) {
        this.client = client;
        this.resolver = resolver;
        this.properties = properties;
    }

    @Override
    public DockerInventory collect() {
        Instant collectedAt = Instant.now();
        List<DockerHost> hosts = new ArrayList<>();
        List<DockerContainer> containers = new ArrayList<>();
        List<DockerNetwork> networks = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        final Map<String, EndpointResolution> resolutions;
        try {
            resolutions = resolver.resolveAll();
        } catch (Exception ex) {
            String code = errorCode(ex);
            log.warn("Portainer endpoint discovery failed errorCode={}", code);
            for (String hostKey : properties.environments().allowlist()) {
                hosts.add(new DockerHost(hostKey, "unavailable", null, null, collectedAt, code));
                errors.add(hostKey + ": " + code);
            }
            return inventory(collectedAt, hosts, containers, networks, errors);
        }

        for (String hostKey : properties.environments().allowlist()) {
            EndpointResolution resolution = resolutions.get(hostKey);
            if (resolution == null || !resolution.isResolved()) {
                String code = resolutionErrorCode(resolution);
                hosts.add(new DockerHost(hostKey, "unavailable", null, null, collectedAt, code));
                errors.add(hostKey + ": " + code);
                continue;
            }

            int endpointId = resolution.endpointId();
            String hostStatus = "ok";
            String hostError = null;
            String engineVersion = null;
            String operatingSystem = null;

            try {
                JsonNode info = client.getDockerInfo(endpointId);
                engineVersion = text(info, "ServerVersion");
                operatingSystem = text(info, "OperatingSystem");
                if (info == null || !info.isObject()) {
                    throw new IllegalStateException("unexpected_response");
                }
            } catch (Exception ex) {
                hostStatus = "partial";
                hostError = errorCode(ex);
                errors.add(hostKey + ": " + hostError);
                log.warn("Portainer Docker info failed host={} errorCode={}", hostKey, hostError);
            }

            try {
                JsonNode containerResponse = client.listContainers(endpointId);
                if (containerResponse == null || !containerResponse.isArray()) {
                    throw new IllegalStateException("unexpected_response");
                }
                for (JsonNode item : containerResponse) {
                    DockerContainer container = mapContainer(hostKey, item);
                    if (container != null) {
                        containers.add(container);
                    }
                }
            } catch (Exception ex) {
                hostStatus = "partial";
                String code = errorCode(ex);
                hostError = hostError == null ? code : hostError;
                errors.add(hostKey + ": containers: " + code);
                log.warn("Portainer container inventory failed host={} errorCode={}", hostKey, code);
            }

            try {
                JsonNode networkResponse = client.listNetworks(endpointId);
                if (networkResponse == null || !networkResponse.isArray()) {
                    throw new IllegalStateException("unexpected_response");
                }
                for (JsonNode item : networkResponse) {
                    DockerNetwork network = mapNetwork(hostKey, item);
                    if (network != null) {
                        networks.add(network);
                    }
                }
            } catch (Exception ex) {
                hostStatus = "partial";
                String code = errorCode(ex);
                hostError = hostError == null ? code : hostError;
                errors.add(hostKey + ": networks: " + code);
                log.warn("Portainer network inventory failed host={} errorCode={}", hostKey, code);
            }

            hosts.add(new DockerHost(hostKey, hostStatus, engineVersion, operatingSystem, collectedAt, hostError));
        }

        return inventory(collectedAt, hosts, containers, networks, errors);
    }

    private static DockerInventory inventory(Instant collectedAt, List<DockerHost> hosts,
            List<DockerContainer> containers, List<DockerNetwork> networks, List<String> errors) {
        long available = hosts.stream().filter(host -> "ok".equals(host.status())).count();
        long partial = hosts.stream().filter(host -> "partial".equals(host.status())).count();
        String status = errors.isEmpty() ? "ok"
                : available + partial == 0 ? "unavailable" : "partial";
        return new DockerInventory(status, collectedAt, hosts, containers, networks, errors);
    }

    private static String resolutionErrorCode(EndpointResolution resolution) {
        if (resolution == null || resolution.status() == Status.NOT_FOUND) {
            return "environment_not_found";
        }
        if (resolution.status() == Status.AMBIGUOUS || resolution.status() == Status.INVALID_ENDPOINT_ID) {
            return "environment_identity_invalid";
        }
        return "environment_unavailable";
    }

    private static DockerContainer mapContainer(String hostKey, JsonNode source) {
        if (source == null || !source.isObject()) {
            return null;
        }
        String id = text(source, "Id");
        if (id == null || id.isBlank()) {
            return null;
        }
        String name = firstName(source.path("Names"));
        String status = text(source, "Status");
        String health = health(status);
        Instant createdAt = epochSeconds(source.get("Created"));
        List<String> attachedNetworks = new ArrayList<>();
        JsonNode networkSettings = source.path("NetworkSettings");
        JsonNode attached = networkSettings.path("Networks");
        if (attached.isObject()) {
            attached.fieldNames().forEachRemaining(attachedNetworks::add);
            attachedNetworks.sort(String::compareTo);
        }
        return new DockerContainer(hostKey, id, name, text(source, "Image"),
                text(source, "State"), status, health, null, createdAt, null, null,
                attachedNetworks, publishedPorts(source.path("Ports")));
    }

    private static DockerNetwork mapNetwork(String hostKey, JsonNode source) {
        if (source == null || !source.isObject()) {
            return null;
        }
        String id = text(source, "Id");
        String name = text(source, "Name");
        if (id == null || name == null) {
            return null;
        }
        List<String> connected = new ArrayList<>();
        JsonNode members = source.path("Containers");
        if (members.isObject()) {
            members.fieldNames().forEachRemaining(memberId -> {
                String memberName = text(members.get(memberId), "Name");
                if (memberName != null && !memberName.isBlank()) {
                    connected.add(memberName);
                } else {
                    connected.add(memberId);
                }
            });
            connected.sort(String::compareTo);
        }
        return new DockerNetwork(hostKey, id, name, text(source, "Driver"), text(source, "Scope"),
                bool(source, "Internal"), bool(source, "Attachable"), connected);
    }

    private static List<PublishedPort> publishedPorts(JsonNode ports) {
        List<PublishedPort> result = new ArrayList<>();
        if (ports == null || !ports.isArray()) {
            return List.of();
        }
        for (JsonNode port : ports) {
            Integer containerPort = integer(port, "PrivatePort");
            Integer hostPort = integer(port, "PublicPort");
            String protocol = text(port, "Type");
            if (containerPort == null || protocol == null) {
                continue;
            }
            if (hostPort != null && hostPort == 0) {
                hostPort = null;
            }
            try {
                result.add(new PublishedPort(containerPort, hostPort, protocol.toLowerCase()));
            } catch (IllegalArgumentException ignored) {
                // Malformed individual port bindings are excluded, not allowed to fail a host.
            }
        }
        return List.copyOf(result);
    }

    private static String firstName(JsonNode names) {
        if (names == null || !names.isArray() || names.size() == 0) {
            return null;
        }
        String value = names.get(0).isString() ? names.get(0).stringValue() : null;
        return value != null && value.startsWith("/") ? value.substring(1) : value;
    }

    private static String health(String status) {
        if (status == null) {
            return null;
        }
        Matcher matcher = HEALTH_PATTERN.matcher(status);
        return matcher.find() ? matcher.group(1).toLowerCase() : null;
    }

    private static Instant epochSeconds(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() <= 0) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(node.longValue());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String text(JsonNode source, String field) {
        if (source == null) {
            return null;
        }
        JsonNode value = source.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    private static Integer integer(JsonNode source, String field) {
        if (source == null) {
            return null;
        }
        JsonNode value = source.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() ? value.intValue() : null;
    }

    private static Boolean bool(JsonNode source, String field) {
        if (source == null) {
            return null;
        }
        JsonNode value = source.get(field);
        return value != null && value.isBoolean() ? value.booleanValue() : null;
    }

    private static String errorCode(Exception ex) {
        if (ex instanceof IllegalStateException && "unexpected_response".equals(ex.getMessage())) {
            return "unexpected_response";
        }
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 401 || status == 403) {
                return "portainer_authentication_failed";
            }
            if (status == 404) {
                return "environment_not_found";
            }
            if (status == 502 || status == 503 || status == 504) {
                return "environment_unavailable";
            }
            return "docker_inventory_request_failed";
        }
        return "portainer_unavailable";
    }
}
