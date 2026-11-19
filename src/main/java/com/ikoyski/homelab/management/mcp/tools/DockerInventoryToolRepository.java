package com.ikoyski.homelab.management.mcp.tools;

import com.ikoyski.homelab.management.mcp.inventory.application.DockerInventoryUseCase;
import com.ikoyski.homelab.management.mcp.inventory.model.DockerInventory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final DockerInventoryUseCase inventory;
    private final ObjectMapper mapper;
    private final Map<String, String> toolDescriptions;

    public DockerInventoryToolRepository(DockerInventoryUseCase inventory, ObjectMapper mapper) {
        this.inventory = inventory;
        this.mapper = mapper;
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put(HOSTS, "List the configured Docker hosts and their availability.");
        definitions.put(CONTAINERS, "List allowlisted Docker container inventory fields across configured hosts.");
        definitions.put(NETWORKS, "List allowlisted Docker network inventory fields across configured hosts.");
        definitions.put(INVENTORY, "Get combined Docker host, container, and network inventory with partial-result information.");
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
    public String execute(String name) {
        try {
            DockerInventory collected = inventory.collect();
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
            log.info("mcp_tool_call_completed tool={}", name);
            return mapper.writeValueAsString(result);
        } catch (RuntimeException ex) {
            log.error("Unable to execute or serialize Docker inventory MCP tool tool={}", name);
            return "Error: Unable to retrieve or serialize Docker inventory.";
        }
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
