package com.ikoyski.homelab.management.mcp.tools;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
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
    private static final Set<String> HOST_FILTER_TOOLS = Set.of(CONTAINERS, NETWORKS);

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
    private final ObjectMapper mapper;
    private final Map<String, String> toolDescriptions;

    public DockerInventoryToolRepository(DockerInventoryUseCase inventory, ObjectMapper mapper) {
        this.inventory = inventory;
        this.mapper = mapper;
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put(HOSTS, "List the configured Docker hosts and their availability.");
        definitions.put(CONTAINERS,
                "List allowlisted Docker container inventory fields across configured hosts. Optionally filter to one configured host with hostKey.");
        definitions.put(NETWORKS,
                "List allowlisted Docker network inventory fields across configured hosts. Optionally filter to one configured host with hostKey.");
        definitions.put(INVENTORY,
                "Get combined Docker host, container, and network inventory with collection timestamps and partial-result information.");
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
        return HOST_FILTER_TOOLS.contains(name) ? HOST_FILTER_SCHEMA : HomelabToolProvider.super.toolInputSchema(name);
    }

    @Override
    public String execute(String name) {
        return execute(name, Map.of());
    }

    @Override
    public String execute(String name, Map<String, Object> arguments) {
        try {
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
            return "Error: Invalid Docker inventory arguments.";
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

    private static Map<String, Object> section(DockerInventory inventory, Object items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", inventory.status());
        result.put("collectedAt", inventory.collectedAt());
        result.put("items", items);
        result.put("errors", inventory.errors());
        return result;
    }
}
