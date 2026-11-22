package com.ikoyski.homelab.management.mcp.tools;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.inventory.application.DockerContainerLogsUseCase;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerContainerLogs;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Read-only inventory tools for Docker data retrieved through Portainer. */
@Component
@ConditionalOnProperty(prefix = "homelab.docker.portainer", name = "base-url")
public class DockerInventoryToolRepository implements HomelabToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DockerInventoryToolRepository.class);
    private static final String HOSTS = "list_docker_hosts";
    private static final String CONTAINERS = "list_docker_containers";
    private static final String NETWORKS = "list_docker_networks";
    private static final String INVENTORY = "get_docker_inventory";
    private static final String CONTAINER_LOGS = "get_docker_container_logs";
    private static final String CONTAINER_STATS = "get_docker_container_stats";
    private static final Set<String> HOST_FILTER_TOOLS = Set.of(CONTAINERS, NETWORKS);
    private static final Map<String, Object> CONTAINER_LOGS_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "hostKey", Map.of("type", "string", "minLength", 1, "maxLength", 63,
                            "description", "Exact configured Docker host key."),
                    "containerName", Map.of("type", "string", "minLength", 1, "maxLength", 255,
                            "description", "Exact discovered container name on the selected host."),
                    "tail", Map.of("type", "integer", "minimum", 1, "maximum", 200,
                            "description", "Maximum number of recent log lines. Defaults to 100; maximum 200.")),
            "required", List.of("hostKey", "containerName"),
            "additionalProperties", false);

    private static final Map<String, Object> CONTAINER_STATS_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "hostKey", Map.of("type", "string", "minLength", 1, "maxLength", 63,
                            "description", "Optional exact configured Docker host key."),
                    "containerName", Map.of("type", "string", "minLength", 1, "maxLength", 255,
                            "description", "Optional exact discovered container name. Omit to sample all containers.")),
            "required", List.of(),
            "additionalProperties", false);

    private static final Map<String, Object> HOST_FILTER_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("hostKey", Map.of(
                    "type", "string",
                    "minLength", 1,
                    "maxLength", 63,
                    "description", "Optional exact configured Docker host key. Omit to query all configured hosts.")),
            "required", List.of(),
            "additionalProperties", false);

    private final DockerInventoryUseCase inventory;
    private final DockerContainerLogsUseCase containerLogs;
    private final ObjectMapper mapper;
    private final Map<String, String> toolDescriptions;

    public DockerInventoryToolRepository(
            DockerInventoryUseCase inventory, DockerContainerLogsUseCase containerLogs, ObjectMapper mapper) {
        this.inventory = inventory;
        this.containerLogs = containerLogs;
        this.mapper = mapper;
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put(HOSTS, "List the configured Docker hosts and their availability.");
        definitions.put(CONTAINERS,
                "List allowlisted Docker container inventory fields across configured hosts. Optionally filter to one configured host with hostKey.");
        definitions.put(NETWORKS,
                "List allowlisted Docker network inventory fields across configured hosts. Optionally filter to one configured host with hostKey.");
        definitions.put(INVENTORY,
                "Get combined Docker host, container, and network inventory with collection timestamps and partial-result information.");
        definitions.put(CONTAINER_LOGS,
                "Retrieve a bounded, non-following snapshot of recent stdout/stderr logs for one exact discovered container on one configured host. Returns best-effort redacted log text; logs may still contain sensitive data.");
        definitions.put(CONTAINER_STATS,
                "Get a live, read-only Docker resource snapshot for containers: CPU percentage, raw memory and estimated working set, memory limit and percentage, network bytes, and block I/O bytes. Optionally filter by exact configured hostKey and/or exact containerName. This is a point-in-time sample, not historical monitoring; null CPU percentage means Docker did not provide usable counter deltas.");
        this.toolDescriptions = Collections.unmodifiableMap(definitions);
    }

    @Override
    public List<String> toolNames() {
        return List.copyOf(toolDescriptions.keySet());
    }

    @Override
    public String toolDescription(String name) {
        return toolDescriptions.get(name);
    }

    @Override
    public Map<String, Object> toolInputSchema(String name) {
        if (HOST_FILTER_TOOLS.contains(name)) {
            return HOST_FILTER_SCHEMA;
        }
        if (CONTAINER_LOGS.equals(name)) {
            return CONTAINER_LOGS_SCHEMA;
        }
        return CONTAINER_STATS.equals(name) ? CONTAINER_STATS_SCHEMA : HomelabToolProvider.super.toolInputSchema(name);
    }

    @Override
    public String execute(String name) {
        return execute(name, Map.of());
    }

    @Override
    public String execute(String name, Map<String, Object> arguments) {
        try {
            if (CONTAINER_STATS.equals(name)) {
                StatsRequest request = statsRequest(arguments);
                return mapper.writeValueAsString(inventory.collectStats(request.hostKey(), request.containerName()));
            }
            if (CONTAINER_LOGS.equals(name)) {
                LogRequest request = logRequest(arguments);
                DockerContainerLogs result = containerLogs.readLogs(
                        request.hostKey(), request.containerName(), request.tail());
                return mapper.writeValueAsString(result);
            }
            String hostKey = requestedHostKey(name, arguments);
            DockerInventory collected = hostKey == null ? inventory.collect() : inventory.collect(hostKey);
            Object result = switch (name) {
                case HOSTS -> section(collected, collected.hosts());
                case CONTAINERS -> section(collected, collected.containers());
                case NETWORKS -> section(collected, collected.networks());
                case INVENTORY -> collected;
                default -> null;
            };
            if (result == null) {
                return "Error: Unknown tool.";
            }
            log.info("mcp_tool_call_completed tool={} hostFilter={}", name, hostKey == null ? "all" : "configured");
            return mapper.writeValueAsString(result);
        } catch (IllegalArgumentException ex) {
            return "Error: Invalid Docker inventory or log arguments.";
        } catch (RuntimeException ex) {
            log.error("Unable to execute or serialize Docker inventory MCP tool tool={}", name);
            return "Error: Unable to retrieve or serialize Docker inventory.";
        }
    }

    private static String requestedHostKey(String name, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (!HOST_FILTER_TOOLS.contains(name)) {
            if (!safeArguments.isEmpty()) {
                throw new IllegalArgumentException("This tool does not accept arguments");
            }
            return null;
        }
        if (safeArguments.isEmpty()) {
            return null;
        }
        if (!safeArguments.keySet().equals(Set.of("hostKey"))) {
            throw new IllegalArgumentException("Only hostKey is supported");
        }
        Object value = safeArguments.get("hostKey");
        if (!(value instanceof String hostKey) || hostKey.isBlank()) {
            throw new IllegalArgumentException("hostKey must be a non-empty string");
        }
        return hostKey;
    }


    private static StatsRequest statsRequest(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (!Set.of("hostKey", "containerName").containsAll(safeArguments.keySet())) {
            throw new IllegalArgumentException("Only hostKey and containerName are supported");
        }
        String hostKey = optionalString(safeArguments.get("hostKey"), "hostKey", 63);
        String containerName = optionalString(safeArguments.get("containerName"), "containerName", 255);
        return new StatsRequest(hostKey, containerName);
    }

    private static String optionalString(Object value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be a non-empty string within its length limit");
        }
        return text;
    }

    private record StatsRequest(String hostKey, String containerName) {
    }

    private static LogRequest logRequest(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        if (!safeArguments.keySet().containsAll(Set.of("hostKey", "containerName"))
                || !Set.of("hostKey", "containerName", "tail").containsAll(safeArguments.keySet())) {
            throw new IllegalArgumentException("hostKey and containerName are required; only tail is optional");
        }
        Object hostValue = safeArguments.get("hostKey");
        Object nameValue = safeArguments.get("containerName");
        if (!(hostValue instanceof String hostKey) || hostKey.isBlank()
                || !(nameValue instanceof String containerName) || containerName.isBlank()) {
            throw new IllegalArgumentException("hostKey and containerName must be non-empty strings");
        }

        int tail = 100;
        if (safeArguments.containsKey("tail")) {
            Object tailValue = safeArguments.get("tail");
            if (!(tailValue instanceof Number number)) {
                throw new IllegalArgumentException("tail must be an integer");
            }
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
                    || numeric < 1 || numeric > 200) {
                throw new IllegalArgumentException("tail must be between 1 and 200");
            }
            tail = (int) numeric;
        }
        return new LogRequest(hostKey, containerName, tail);
    }

    private record LogRequest(String hostKey, String containerName, int tail) {
    }

    private static Map<String, Object> section(DockerInventory inventory, Object items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", inventory.status());
        result.put("collectedAt", inventory.collectedAt());
        result.put("items", items);
        result.put("errors", inventory.errors());
        return result;
    }
}
