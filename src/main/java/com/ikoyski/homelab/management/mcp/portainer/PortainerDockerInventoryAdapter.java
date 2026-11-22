package com.ikoyski.homelab.management.mcp.portainer;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryProvider;
import com.ikoyski.homelab.management.mcp.inventory.application.DockerContainerLogsProvider;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerLogs;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerStats;
import com.ikoyski.homelab.management.mcp.portainer.client.DockerLogPayload;
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
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
public class PortainerDockerInventoryAdapter implements DockerInventoryProvider, DockerContainerLogsProvider {
    private static final Logger log = LoggerFactory.getLogger(PortainerDockerInventoryAdapter.class);
    private static final Pattern HEALTH_PATTERN = Pattern.compile("\\b(healthy|unhealthy|starting)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> HEALTH_STATES = Set.of("healthy", "unhealthy", "starting");
    private static final int MAX_CONCURRENT_CONTAINER_INSPECTIONS = 4;

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
        return collect(null);
    }

    @Override
    public DockerInventory collect(String hostKey) {
        List<String> requestedHosts = requestedHosts(hostKey);
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
            for (String requestedHost : requestedHosts) {
                hosts.add(new DockerHost(requestedHost, "unavailable", null, null, collectedAt, code));
                errors.add(requestedHost + ": " + code);
            }
            return inventory(collectedAt, hosts, containers, networks, errors);
        }

        try (ExecutorService inspectionExecutor =
                Executors.newFixedThreadPool(MAX_CONCURRENT_CONTAINER_INSPECTIONS)) {
            for (String requestedHost : requestedHosts) {
                EndpointResolution resolution = resolutions.get(requestedHost);
                if (resolution == null || !resolution.isResolved()) {
                    String code = resolutionErrorCode(resolution);
                    hosts.add(new DockerHost(requestedHost, "unavailable", null, null, collectedAt, code));
                    errors.add(requestedHost + ": " + code);
                    continue;
                }

                int endpointId = resolution.endpointId();
                String hostStatus = "ok";
                String hostError = null;
                String engineVersion = null;
                String operatingSystem = null;

                try {
                    JsonNode info = client.getDockerInfo(endpointId);
                    if (info == null || !info.isObject()) {
                        throw new IllegalStateException("unexpected_response");
                    }
                    engineVersion = text(info, "ServerVersion");
                    operatingSystem = text(info, "OperatingSystem");
                } catch (Exception ex) {
                    hostStatus = "partial";
                    hostError = errorCode(ex);
                    errors.add(requestedHost + ": " + hostError);
                    log.warn("Portainer Docker info failed host={} errorCode={}", requestedHost, hostError);
                }

                boolean containerDetailsUnavailable = false;
                try {
                    JsonNode containerResponse = client.listContainers(endpointId);
                    if (containerResponse == null || !containerResponse.isArray()) {
                        throw new IllegalStateException("unexpected_response");
                    }
                    List<PendingContainerInspection> pending = new ArrayList<>();
                    for (JsonNode item : containerResponse) {
                        DockerContainer summary = mapContainer(requestedHost, item);
                        if (summary != null) {
                            Future<ContainerEnrichment> future = inspectionExecutor.submit(
                                    () -> inspectContainer(requestedHost, endpointId, summary, collectedAt));
                            pending.add(new PendingContainerInspection(summary, future));
                        }
                    }

                    boolean interrupted = false;
                    for (PendingContainerInspection item : pending) {
                        if (interrupted) {
                            item.future().cancel(true);
                            containers.add(item.summary());
                            containerDetailsUnavailable = true;
                            continue;
                        }
                        try {
                            ContainerEnrichment enrichment = item.future().get();
                            containers.add(enrichment.container());
                            containerDetailsUnavailable |= !enrichment.enriched();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            interrupted = true;
                            item.future().cancel(true);
                            containers.add(item.summary());
                            containerDetailsUnavailable = true;
                        } catch (ExecutionException | CancellationException ex) {
                            containers.add(item.summary());
                            containerDetailsUnavailable = true;
                        }
                    }
                } catch (Exception ex) {
                    hostStatus = "partial";
                    String code = errorCode(ex);
                    hostError = hostError == null ? code : hostError;
                    errors.add(requestedHost + ": containers: " + code);
                    log.warn("Portainer container inventory failed host={} errorCode={}", requestedHost, code);
                }
                if (containerDetailsUnavailable) {
                    hostStatus = "partial";
                    hostError = hostError == null ? "container_details_unavailable" : hostError;
                    errors.add(requestedHost + ": container_details_unavailable");
                    log.warn("Portainer container detail enrichment incomplete host={}", requestedHost);
                }

                try {
                    JsonNode networkResponse = client.listNetworks(endpointId);
                    if (networkResponse == null || !networkResponse.isArray()) {
                        throw new IllegalStateException("unexpected_response");
                    }
                    for (JsonNode item : networkResponse) {
                        DockerNetwork network = mapNetwork(requestedHost, item);
                        if (network != null) {
                            networks.add(network);
                        }
                    }
                } catch (Exception ex) {
                    hostStatus = "partial";
                    String code = errorCode(ex);
                    hostError = hostError == null ? code : hostError;
                    errors.add(requestedHost + ": networks: " + code);
                    log.warn("Portainer network inventory failed host={} errorCode={}", requestedHost, code);
                }

                hosts.add(new DockerHost(requestedHost, hostStatus, engineVersion, operatingSystem,
                        collectedAt, hostError));
            }
        }

        return inventory(collectedAt, hosts, containers, addContainerMembership(networks, containers), errors);
    }


    @Override
    public DockerContainerLogs readLogs(String hostKey, String containerName, int tail) {
        if (hostKey == null || !properties.environments().allowlist().contains(hostKey)) {
            return logFailure(hostKey, containerName, tail, "host_not_configured");
        }
        if (containerName == null || !containerName.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}")) {
            return logFailure(hostKey, containerName, tail, "invalid_container_name");
        }
        if (tail < 1 || tail > 200) {
            return logFailure(hostKey, containerName, tail, "invalid_tail");
        }

        try {
            EndpointResolution resolution = resolver.resolveAll().get(hostKey);
            if (resolution == null || !resolution.isResolved()) {
                return logFailure(hostKey, containerName, tail, resolutionErrorCode(resolution));
            }

            JsonNode response = client.listContainers(resolution.endpointId());
            if (response == null || !response.isArray()) {
                return logFailure(hostKey, containerName, tail, "unexpected_response");
            }

            String discoveredContainerId = null;
            int matches = 0;
            for (JsonNode item : response) {
                JsonNode names = item.path("Names");
                if (!names.isArray()) {
                    continue;
                }
                boolean exactMatch = false;
                for (JsonNode nameNode : names) {
                    if (!nameNode.isString()) {
                        continue;
                    }
                    String discoveredName = nameNode.stringValue();
                    if (discoveredName.startsWith("/")) {
                        discoveredName = discoveredName.substring(1);
                    }
                    if (containerName.equals(discoveredName)) {
                        exactMatch = true;
                        break;
                    }
                }
                if (exactMatch) {
                    matches++;
                    discoveredContainerId = text(item, "Id");
                }
            }

            if (matches == 0) {
                return logFailure(hostKey, containerName, tail, "container_not_found");
            }
            if (matches != 1 || discoveredContainerId == null
                    || !discoveredContainerId.matches("[a-fA-F0-9]{12,64}")) {
                return logFailure(hostKey, containerName, tail, "container_identity_ambiguous");
            }

            DockerLogPayload payload = client.getContainerLogs(
                    resolution.endpointId(), discoveredContainerId, tail);
            String decoded = DockerLogDecoder.decode(payload.bytes(), payload.truncated());
            DockerLogRedactor.Redaction redaction = DockerLogRedactor.redact(decoded);
            return new DockerContainerLogs("ok", hostKey, containerName, tail,
                    payload.bytes().length, payload.truncated(), redaction.redactedValueCount(),
                    redaction.value(), null);
        } catch (PortainerApiClient.DockerLogRequestException ex) {
            String code = ex.statusCode() == 404 ? "container_not_found"
                    : ex.statusCode() == 401 || ex.statusCode() == 403 ? "portainer_authentication_failed"
                    : ex.statusCode() == 502 || ex.statusCode() == 503 || ex.statusCode() == 504
                    ? "environment_unavailable" : "logs_unavailable";
            log.warn("Portainer Docker logs request failed errorCode={}", code);
            return logFailure(hostKey, containerName, tail, code);
        } catch (Exception ex) {
            String code = ex instanceof RestClientResponseException ? errorCode(ex) : "logs_unavailable";
            log.warn("Portainer Docker log retrieval failed errorCode={}", code);
            return logFailure(hostKey, containerName, tail, code);
        }
    }

    @Override
    public List<DockerContainerStats> collectStats(String hostKey, String containerName) {
        List<String> hosts = requestedHosts(hostKey);
        if (containerName != null && !containerName.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}")) {
            throw new IllegalArgumentException("containerName must be an exact discovered name");
        }
        List<DockerContainerStats> result = new ArrayList<>();
        Map<String, EndpointResolution> resolutions;
        try {
            resolutions = resolver.resolveAll();
        } catch (Exception ex) {
            String code = errorCode(ex);
            for (String host : hosts) {
                result.add(statsFailure(host, containerName, null, code));
            }
            return List.copyOf(result);
        }

        // Bound one tool invocation so a large fleet cannot fan out unbounded upstream calls.
        final int maxContainers = 100;
        for (String host : hosts) {
            EndpointResolution resolution = resolutions.get(host);
            if (resolution == null || !resolution.isResolved()) {
                result.add(statsFailure(host, containerName, null, resolutionErrorCode(resolution)));
                continue;
            }
            try {
                JsonNode listed = client.listContainers(resolution.endpointId());
                if (listed == null || !listed.isArray()) {
                    throw new IllegalStateException("unexpected_response");
                }
                boolean exactContainerFound = false;
                for (JsonNode item : listed) {
                    String id = text(item, "Id");
                    String name = firstName(item.path("Names"));
                    if (id == null || !id.matches("[a-fA-F0-9]{12,64}") || name == null
                            || (containerName != null && !containerName.equals(name))) {
                        continue;
                    }
                    exactContainerFound = true;
                    if (!"running".equalsIgnoreCase(text(item, "State"))) {
                        if (containerName != null) {
                            result.add(statsFailure(host, name, id, "container_not_running"));
                        }
                        continue;
                    }
                    if (result.size() >= maxContainers - 1) {
                        result.add(statsFailure(host, null, null, "result_limit_reached"));
                        return List.copyOf(result);
                    }
                    try {
                        JsonNode snapshot = client.getContainerStats(resolution.endpointId(), id);
                        result.add(mapStats(host, name, id, snapshot));
                    } catch (Exception ex) {
                        result.add(statsFailure(host, name, id, errorCode(ex)));
                    }
                }
                if (containerName != null && !exactContainerFound) {
                    result.add(statsFailure(host, containerName, null, "container_not_found"));
                }
            } catch (Exception ex) {
                result.add(statsFailure(host, containerName, null, errorCode(ex)));
            }
        }
        return List.copyOf(result);
    }

    private static DockerContainerStats statsFailure(String host, String name, String id, String code) {
        return new DockerContainerStats(host, name, id, "unavailable", Instant.now(),
                null, null, null, null, null, null, null, null, null, null, code);
    }

    private static DockerContainerStats mapStats(String host, String name, String id, JsonNode stats) {
        if (stats == null || !stats.isObject()) {
            return statsFailure(host, name, id, "unexpected_response");
        }
        JsonNode cpu = stats.path("cpu_stats");
        JsonNode previousCpu = stats.path("precpu_stats");
        long cpuDelta = numeric(cpu.path("cpu_usage").path("total_usage"))
                - numeric(previousCpu.path("cpu_usage").path("total_usage"));
        long systemDelta = numeric(cpu.path("system_cpu_usage"))
                - numeric(previousCpu.path("system_cpu_usage"));
        long onlineCpus = numeric(cpu.path("online_cpus"));
        if (onlineCpus <= 0) {
            JsonNode perCpu = cpu.path("cpu_usage").path("percpu_usage");
            onlineCpus = perCpu.isArray() ? perCpu.size() : 0;
        }
        Double cpuPercent = cpuDelta >= 0 && systemDelta > 0 && onlineCpus > 0
                ? (double) cpuDelta / (double) systemDelta * onlineCpus * 100.0 : null;

        JsonNode memory = stats.path("memory_stats");
        Long memoryUsage = optionalNumeric(memory.get("usage"));
        Long memoryLimit = optionalNumeric(memory.get("limit"));
        JsonNode memoryDetails = memory.path("stats");
        Long cache = optionalNumeric(memoryDetails.get("cache"));
        if (cache == null) {
            cache = optionalNumeric(memoryDetails.get("inactive_file"));
        }
        if (cache == null) {
            cache = optionalNumeric(memoryDetails.get("total_inactive_file"));
        }
        Long workingSet = memoryUsage == null ? null
                : Math.max(0L, memoryUsage - (cache == null ? 0L : cache));
        Double memoryPercent = workingSet != null && memoryLimit != null && memoryLimit > 0
                ? (double) workingSet / memoryLimit * 100.0 : null;

        long rx = 0L;
        long tx = 0L;
        JsonNode networks = stats.path("networks");
        if (networks.isObject()) {
            for (String network : networks.propertyNames()) {
                JsonNode counters = networks.get(network);
                rx = saturatedAdd(rx, numeric(counters.path("rx_bytes")));
                tx = saturatedAdd(tx, numeric(counters.path("tx_bytes")));
            }
        }

        long blockRead = 0L;
        long blockWrite = 0L;
        JsonNode ioEntries = stats.path("blkio_stats").path("io_service_bytes_recursive");
        if (ioEntries.isArray()) {
            for (JsonNode entry : ioEntries) {
                long value = numeric(entry.path("value"));
                String operation = text(entry, "op");
                if ("read".equalsIgnoreCase(operation)) {
                    blockRead = saturatedAdd(blockRead, value);
                } else if ("write".equalsIgnoreCase(operation)) {
                    blockWrite = saturatedAdd(blockWrite, value);
                }
            }
        }
        return new DockerContainerStats(host, name, id, "ok", Instant.now(), cpuPercent,
                memoryUsage, workingSet, memoryLimit, memoryPercent, cache, rx, tx,
                blockRead, blockWrite, null);
    }

    private static long numeric(JsonNode node) {
        return node != null && node.isNumber() && node.canConvertToLong()
                ? Math.max(0L, node.longValue()) : 0L;
    }

    private static Long optionalNumeric(JsonNode node) {
        return node != null && node.isNumber() && node.canConvertToLong()
                ? Math.max(0L, node.longValue()) : null;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static DockerContainerLogs logFailure(String hostKey, String containerName, int tail, String code) {
        return new DockerContainerLogs("unavailable", hostKey, containerName, tail,
                0, false, 0, "", code);
    }

    private List<String> requestedHosts(String hostKey) {
        List<String> configuredHosts = properties.environments().allowlist();
        if (hostKey == null) {
            return configuredHosts;
        }
        if (hostKey.isBlank() || !configuredHosts.contains(hostKey)) {
            throw new IllegalArgumentException("hostKey must exactly match a configured Docker host");
        }
        return List.of(hostKey);
    }

    private ContainerEnrichment inspectContainer(String hostKey, int endpointId,
            DockerContainer summary, Instant collectedAt) {
        try {
            JsonNode inspect = client.inspectContainer(endpointId, summary.id());
            if (inspect == null || !inspect.isObject()) {
                throw new IllegalStateException("unexpected_response");
            }
            return new ContainerEnrichment(enrichContainer(summary, inspect, collectedAt), true);
        } catch (Exception ex) {
            log.warn("Portainer container detail request failed host={} errorCode={}", hostKey, errorCode(ex));
            return new ContainerEnrichment(summary, false);
        }
    }

    private static DockerContainer enrichContainer(DockerContainer summary, JsonNode inspect, Instant collectedAt) {
        JsonNode state = inspect.get("State");
        Integer restartCount = integer(inspect, "RestartCount");
        Instant startedAt = parseDockerTimestamp(text(state, "StartedAt"));

        Boolean running = bool(state, "Running");
        if (running == null) {
            String inspectState = text(state, "Status");
            running = "running".equalsIgnoreCase(inspectState)
                    || "running".equalsIgnoreCase(summary.state());
        }
        Long uptimeSeconds = null;
        if (Boolean.TRUE.equals(running) && startedAt != null && !startedAt.isAfter(collectedAt)) {
            long elapsed = Duration.between(startedAt, collectedAt).getSeconds();
            if (elapsed >= 0) {
                uptimeSeconds = elapsed;
            }
        }

        String health = inspectHealth(state, summary.health());
        return new DockerContainer(summary.hostKey(), summary.id(), summary.name(), summary.image(),
                summary.state(), summary.status(), health, restartCount, summary.createdAt(), startedAt,
                uptimeSeconds, summary.networks(), summary.publishedPorts());
    }

    private static String inspectHealth(JsonNode state, String summaryHealth) {
        if (state == null || !state.isObject()) {
            return summaryHealth;
        }
        JsonNode health = state.get("Health");
        if (health == null || health.isNull()) {
            return "not_configured";
        }
        String value = text(health, "Status");
        if (value == null) {
            return "unknown";
        }
        String normalized = value.toLowerCase();
        return HEALTH_STATES.contains(normalized) ? normalized : "unknown";
    }

    private static List<DockerNetwork> addContainerMembership(
            List<DockerNetwork> networks, List<DockerContainer> containers) {
        Map<HostNetworkKey, Set<String>> derivedMembership = new HashMap<>();
        for (DockerContainer container : containers) {
            String memberName = container.name() == null || container.name().isBlank()
                    ? container.id() : container.name();
            for (String networkName : container.networks()) {
                derivedMembership.computeIfAbsent(new HostNetworkKey(container.hostKey(), networkName),
                        ignored -> new TreeSet<>()).add(memberName);
            }
        }

        List<DockerNetwork> enriched = new ArrayList<>(networks.size());
        for (DockerNetwork network : networks) {
            Set<String> members = new TreeSet<>(network.connectedContainers());
            members.addAll(derivedMembership.getOrDefault(
                    new HostNetworkKey(network.hostKey(), network.name()), Set.of()));
            enriched.add(new DockerNetwork(network.hostKey(), network.id(), network.name(), network.driver(),
                    network.scope(), network.internal(), network.attachable(), List.copyOf(members)));
        }
        return List.copyOf(enriched);
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
            attached.propertyNames().forEach(attachedNetworks::add);
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
        Set<String> connected = new LinkedHashSet<>();
        JsonNode members = source.path("Containers");
        if (members.isObject()) {
            members.propertyNames().forEach(memberId -> {
                String memberName = text(members.get(memberId), "Name");
                connected.add(memberName != null && !memberName.isBlank() ? memberName : memberId);
            });
        }
        return new DockerNetwork(hostKey, id, name, text(source, "Driver"), text(source, "Scope"),
                bool(source, "Internal"), bool(source, "Attachable"), connected.stream().sorted().toList());
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

    private static Instant parseDockerTimestamp(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-01-01T00:00:00")) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
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

    private record HostNetworkKey(String hostKey, String networkName) {
    }

    private record PendingContainerInspection(
            DockerContainer summary, Future<ContainerEnrichment> future) {
    }

    private record ContainerEnrichment(DockerContainer container, boolean enriched) {
    }
}
